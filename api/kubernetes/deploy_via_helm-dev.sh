#!/bin/bash

set -eo errexit

#
# Dev version for deploying the Helm Charts using the local Charts folders
#

help() {
  echo
  echo "This script takes at least 3 parameters."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- NAMESPACE | string | name of the targeted namespace. Generated when not set"
  echo "- ARGO_MINIO_ACCESS_KEY | string | AccessKey for MinIO. Generated when not set"
  echo "- ARGO_MINIO_SECRET_KEY | string | SecretKey for MinIO. Generated when not set"
  echo "- ARGO_MINIO_REQUESTS_MEMORY | units of bytes (default is 4Gi) | Memory requests for the Argo MinIO server"
  echo
  echo "Usage: ./$(basename "$0") NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 3 ]]; then
  help
  exit 1
fi

if [ -z "$1" ];
  then
    export NAMESPACE="phoenix"
else
  export NAMESPACE="$1"
fi

export API_VERSION="$3"

HELM_CHARTS_BASE_PATH=$(realpath "$(dirname "$0")")

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# Kubernetes Dashboard
echo -- Kubernetes Dashboard
cat <<EOF > values-kubernetes-dashboard-rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin-user
  namespace: phoenix
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin-user
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: admin-user
  namespace: phoenix

EOF
kubectl --namespace "${NAMESPACE}" apply --validate=false -f values-kubernetes-dashboard-rbac.yaml

echo Dashboard token:
kubectl -n ${NAMESPACE} get secret $(kubectl -n ${NAMESPACE} get sa/admin-user -o jsonpath="{.secrets[0].name}") -o go-template="{{.data.token | base64decode}}"
echo

helm repo add kubernetes-dashboard https://kubernetes.github.io/dashboard/
cat <<EOF > values-kubernetes-dashboard.yaml
ingress:
  enabled: false
service:
  externalPort: 80
serviceAccount:
  create: false
  name: admin-user
protocolHttp: true
podLabels:
  "networking/traffic-allowed": "yes"
nodeSelector:
  "cosmotech.com/tier": "services"
tolerations:
- key: "vendor"
  operator: "Equal"
  value: "cosmotech"
  effect: "NoSchedule"
resources:
  requests:
    cpu: 100m
    memory: 200Mi
  limits:
    cpu: 2
    memory: 200Mi

EOF
helm upgrade --install -n ${NAMESPACE} kubernetes-dashboard kubernetes-dashboard/kubernetes-dashboard --values values-kubernetes-dashboard.yaml

# Redis Cluster
helm repo add bitnami https://charts.bitnami.com/bitnami

helm upgrade --install \
    --namespace ${NAMESPACE} cosmotechredis bitnami/redis \
    --values https://raw.githubusercontent.com/Cosmo-Tech/cosmotech-redis/main/values-cosmotech-cluster.yaml \
    --set replica.replicaCount=1 \
    --set master.nodeSelector."cosmotech\\.com/tier"=db \
    --set master.resources.requests.cpu=500m \
    --set master.resources.limits.cpu=1 \
    --set master.resources.requests.memory=512Mi \
    --set master.resources.limits.memory=1024Mi \
    --set replica.nodeSelector."cosmotech\\.com/tier"=db \
    --set replica.resources.requests.cpu=500m \
    --set replica.resources.limits.cpu=1 \
    --set replica.resources.requests.memory=512Mi \
    --set replica.resources.limits.memory=1024Mi \
    --wait \
    --timeout 10m0s

# Redis Insight
REDIS_INSIGHT_HELM_CHART="${HELM_CHARTS_BASE_PATH}/charts/redisinsight-chart.tgz"
wget https://docs.redis.com/latest/pkgs/redisinsight-chart-0.1.0.tgz  -O ${REDIS_INSIGHT_HELM_CHART}

helm upgrade --install \
   --namespace ${NAMESPACE} redisinsight ${REDIS_INSIGHT_HELM_CHART} \
   --set service.type=NodePort \
   --set nodeSelector."cosmotech\\.com/tier"=services \
    --set resources.requests.cpu=100m \
    --set resources.limits.cpu=1 \
    --set resources.requests.memory=128Mi \
    --set resources.limits.memory=128Mi \
   --wait \
   --timeout 5m0s

# Argo
export ARGO_RELEASE_NAME=argo
export ARGO_RELEASE_NAMESPACE="${NAMESPACE}"
export ARGO_POSTGRESQL_PASSWORD="$2"
helm dependency update "${HELM_CHARTS_BASE_PATH}/csm-argo"
# Default memory request in MinIO Chart is 4Gi, which may not work in clusters with lower resources
envsubst < "${HELM_CHARTS_BASE_PATH}/csm-argo/values.yaml" | \
    helm upgrade --install "${ARGO_RELEASE_NAME}" "${HELM_CHARTS_BASE_PATH}/csm-argo" \
        --namespace "${NAMESPACE}" \
        --values - \
        --set argo.minio.resources.requests.memory="${ARGO_MINIO_REQUESTS_MEMORY:-128Mi}" \
        --set argo.minio.resources.limits.memory="${ARGO_MINIO_REQUESTS_MEMORY:-256Mi}" \
        --set argo.minio.resources.requests.cpu="100m" \
        --set argo.minio.resources.limits.cpu="1" \
        --set argo.minio.accessKey="${ARGO_MINIO_ACCESS_KEY:-}" \
        --set argo.minio.secretKey="${ARGO_MINIO_SECRET_KEY:-}" \
        --set argo.controller.nodeSelector."cosmotech\\.com/tier"=services \
        --set argo.controller.resources.requests.memory="64Mi" \
        --set argo.controller.resources.limits.memory="128Mi" \
        --set argo.controller.resources.requests.cpu="100m" \
        --set argo.controller.resources.limits.cpu="1" \
        --set argo.server.nodeSelector."cosmotech\\.com/tier"=services \
        --set argo.server.resources.requests.memory="64Mi" \
        --set argo.server.resources.limits.memory="128Mi" \
        --set argo.server.resources.requests.cpu="100m" \
        --set argo.server.resources.limits.cpu="1" \
        --set argo.minio.nodeSelector."cosmotech\\.com/tier"=services \
        --set postgresql.primary.nodeSelector."cosmotech\\.com/tier"=db \
        --set postgresql.readReplicas.nodeSelector."cosmotech\\.com/tier"=db \
        --set postgresql.resources.requests.memory="64Mi" \
        --set postgresql.resources.limits.memory="256Mi" \
        --set postgresql.resources.requests.cpu="250m" \
        --set postgresql.resources.limits.cpu="1" \

# cosmotech-api
export COSMOTECH_API_RELEASE_NAME="cosmotech-api-${API_VERSION}"
# shellcheck disable=SC2140
helm upgrade --install "${COSMOTECH_API_RELEASE_NAME}" "${HELM_CHARTS_BASE_PATH}/helm-chart" \
    --namespace "${NAMESPACE}" \
    --values "${HELM_CHARTS_BASE_PATH}/helm-chart/values-dev.yaml" \
    --set config.csm.platform.commit-id="$(git rev-parse --short HEAD || "")" \
    --set config.csm.platform.vcs-ref="$(git rev-parse --abbrev-ref HEAD || "")" \
    --set image.tag="$API_VERSION" \
    --set api.version="$API_VERSION" \
    --set config.csm.platform.argo.base-uri="http://${ARGO_RELEASE_NAME}-server.${NAMESPACE}.svc.cluster.local:2746" \
    --set config.csm.platform.argo.workflows.namespace="${NAMESPACE}" \
    --set podAnnotations."com\.cosmotech/deployed-at-timestamp"="\"$(date +%s)\"" \
    --set nodeSelector."cosmotech\\.com/tier"=services \
    "${@:4}"
