#!/bin/bash

set -eo errexit

#
# Dev version for deploying the Helm Charts using the local Charts folders
#

help() {
  echo
  echo "This script takes at least 4 parameters."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- API_IMAGE_TAG | string | V1, V2, latest"
  echo "- NAMESPACE | string | name of the targeted namespace. Generated when not set"
  echo "- ARGO_MINIO_ACCESS_KEY | string | AccessKey for MinIO. Generated when not set"
  echo "- ARGO_MINIO_SECRET_KEY | string | SecretKey for MinIO. Generated when not set"
  echo "- ARGO_MINIO_REQUESTS_MEMORY | units of bytes (default is 4Gi) | Memory requests for the Argo MinIO server"
  echo "- PROM_STORAGE_CLASS_NAME | storage class name for the prometheus PVC (default is standard)"
  echo "- PROM_STORAGE_RESOURCE_REQUEST | size requested for prometheusPVC (default is 10Gi)"
  echo "- PROM_CPU_MEM_LIMITS | memory size limit for prometheus (default is 2Gi)"
  echo "- PROM_CPU_MEM_REQUESTS | memory size requested for prometheus (default is 2Gi)"
  echo "- PROM_REPLICAS_NUMBER | number of prometheus replicas (default is 1)"
  echo "- PROM_ADMIN_PASSWORD | admin password for grafana (generated if not specified)"
  echo
  echo "Usage: ./$(basename "$0") API_IMAGE_TAG NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 4 ]]; then
  help
  exit 1
fi

export API_IMAGE_TAG="$1"
if [ -z "$2" ];
  then
    export NAMESPACE="phoenix"
else
  export NAMESPACE="$2"
fi
export ARGO_POSTGRESQL_PASSWORD="$3"
export API_VERSION="$4"

export ARGO_RELEASE_NAME=argocsmv2
export MINIO_RELEASE_NAME=miniocsmv2
export POSTGRES_RELEASE_NAME=postgrescsmv2
export ARGO_VERSION="0.16.6"
export MINIO_VERSION="8.0.10"
export POSTGRESQL_VERSION="11.6.12"

export ARGO_DATABASE=argo_workflows
export ARGO_POSTGRESQL_USER=argo
export ARGO_BUCKET_NAME=argo-workflows
export ARGO_SERVICE_ACCOUNT=workflowcsmv2

HELM_CHARTS_BASE_PATH=$(realpath "$(dirname "$0")")

WORKING_DIR=$(mktemp -d -t cosmotech-api-helm-XXXXXXXXXX)
echo "[info] Working directory: ${WORKING_DIR}"
pushd "${WORKING_DIR}"

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

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
   --timeout 10m0s

REDIS_PASSWORD=$(kubectl get secret --namespace ${NAMESPACE} cosmotechredis -o jsonpath="{.data.redis-password}" | base64 --decode)

# Minio
cat <<EOF > values-minio.yaml
fullnameOverride: ${MINIO_RELEASE_NAME}
defaultBucket:
  enabled: true
  name: ${ARGO_BUCKET_NAME}
persistence:
  enabled: true
resources:
  requests:
    memory: "${ARGO_MINIO_REQUESTS_MEMORY:-2Gi}"
    cpu: "100m"
  limits:
    memory: "${ARGO_MINIO_REQUESTS_MEMORY:-2Gi}"
    cpu: "1"
service:
  type: ClusterIP
DeploymentUpdate:
  # As Minio uses a ReadWriteOnce PVC by default, using a RollingUpdate strategy will not
  # work if the new pod is scheduled on a different node.
  # It is possible to force the replicas scheduling on a same node, but this requires that node
  # to have a minimum of 4Gi additional memory available, which is the default memory request
  # set by Argo.
  type: Recreate
networkPolicy:
  # Enabling networking policy returns the following error: unable to recognize "": no matches for kind "NetworkPolicy" in version "networking.k8s.io/v1beta1"
  # => will use the 'networking/traffic-allowed: "yes"' label instead to allow traffic.
  enabled: false
  allowExternal: true
podLabels:
  networking/traffic-allowed: "yes"
nodeSelector:
  cosmotech.com/tier: services
accessKey: "${ARGO_MINIO_ACCESS_KEY:-}"
secretKey: "${ARGO_MINIO_SECRET_KEY:-}"
EOF

helm repo add minio https://helm.min.io/
helm upgrade --install ${MINIO_RELEASE_NAME} minio/minio --namespace ${NAMESPACE} --version ${MINIO_VERSION} --values values-minio.yaml

# Postgres
cat <<EOF > values-postgresql.yaml
auth:
  username: "${ARGO_POSTGRESQL_USER}"
  password: "${ARGO_POSTGRESQL_PASSWORD}"
  database: ${ARGO_DATABASE}
primary:
  podLabels:
    "networking/traffic-allowed": "yes"
  nodeSelector:
    cosmotech.com/tier: db
readReplicas:
  nodeSelector:
    cosmotech.com/tier: db
resources:
  requests:
    memory: "64Mi"
    cpu: "250m"
  limits:
    memory: "256Mi"
    cpu: "1"

EOF

helm repo add bitnami https://charts.bitnami.com/bitnami
helm upgrade --install -n ${NAMESPACE} ${POSTGRES_RELEASE_NAME} bitnami/postgresql --version ${POSTGRESQL_VERSION} --values values-postgresql.yaml

export ARGO_POSTGRESQL_SECRET_NAME=argo-postgres-config
cat <<EOF > postgres-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  labels:
    app: postgres
  name: ${ARGO_POSTGRESQL_SECRET_NAME}
stringData:
  password: "${ARGO_POSTGRESQL_PASSWORD}"
  username: "${ARGO_POSTGRESQL_USER}"
type: Opaque

EOF
kubectl apply -n ${NAMESPACE} -f postgres-secret.yaml

# Argo
cat <<EOF > values-argo.yaml
images:
  pullPolicy: IfNotPresent
workflow:
  serviceAccount:
    create: true
    name: ${ARGO_SERVICE_ACCOUNT}
  rbac:
    create: true
executor:
  env:
  - name: RESOURCE_STATE_CHECK_INTERVAL
    value: 1s
  - name: WAIT_CONTAINER_STATUS_CHECK_INTERVAL
    value: 1s
  - name: RECENTLY_STARTED_POD_DURATION
    value: 1s
useDefaultArtifactRepo: true
artifactRepository:
  archiveLogs: true
  s3:
    bucket: ${ARGO_BUCKET_NAME}
    endpoint: ${MINIO_RELEASE_NAME}.${NAMESPACE}.svc.cluster.local:9000
    insecure: true
    accessKeySecret:
      name: ${MINIO_RELEASE_NAME}
      key: accesskey
    secretKeySecret:
      name: ${MINIO_RELEASE_NAME}
      key: secretkey
server:
  extraArgs:
  - --auth-mode=server
  secure: false
  podLabels:
    networking/traffic-allowed: "yes"
  nodeSelector:
    cosmotech.com/tier: services
  resources:
    requests:
      memory: "64Mi"
      cpu: "100m"
    limits:
      memory: "128Mi"
      cpu: "1"
controller:
  nodeSelector:
    cosmotech.com/tier: services
  podLabels:
    networking/traffic-allowed: "yes"
  resources:
    requests:
      memory: "64Mi"
      cpu: "100m"
    limits:
      memory: "128Mi"
      cpu: "1"
  containerRuntimeExecutor: k8sapi
  metricsConfig:
    enabled: true
  workflowDefaults:
    spec:
      # make sure workflows do not run forever. Default limit set is 7 days (604800 seconds)
      activeDeadlineSeconds: 604800
      ttlStrategy:
        # keep workflows that succeeded for 1d (86400 seconds).
        # We can still view them since they are archived.
        secondsAfterSuccess: 86400
        # keep workflows that have completed (either successfully or not) for 3d (259200 seconds).
        # We can still view them since they are archived.
        secondsAfterCompletion: 259200
      podGC:
        # Delete pods when workflows are successful.
        # We can still access their logs and artifacts since they are archived.
        # One of "OnPodCompletion", "OnPodSuccess", "OnWorkflowCompletion", "OnWorkflowSuccess"
        strategy: OnWorkflowSuccess
      volumeClaimGC:
        # Delete PVCs when workflows are done. However, due to Kubernetes PVC Protection,
        # such PVCs will just be marked as Terminating, until no pod is using them.
        # Pod deletion (either via the Pod GC strategy or the TTL strategy) will allow to free up
        # attached PVCs.
        # One of "OnWorkflowCompletion", "OnWorkflowSuccess"
        strategy: OnWorkflowCompletion
  persistence:
    archive: true
    postgresql:
      host: "${POSTGRES_RELEASE_NAME}-postgresql"
      database: ${ARGO_DATABASE}
      tableName: workflows
      userNameSecret:
        name: "${ARGO_POSTGRESQL_SECRET_NAME}"
        key: username
      passwordSecret:
        name: "${ARGO_POSTGRESQL_SECRET_NAME}"
        key: password
mainContainer:
  imagePullPolicy: IfNotPresent

EOF

helm repo add argo https://argoproj.github.io/argo-helm
helm upgrade --install -n ${NAMESPACE} ${ARGO_RELEASE_NAME} argo/argo-workflows --version ${ARGO_VERSION} --values values-argo.yaml

popd

# cosmotech-api
export COSMOTECH_API_RELEASE_NAME="cosmotech-api-${API_VERSION}"

cat <<EOF > values-cosmotech-api-deploy.yaml
api:
  version: "$API_VERSION"
replicaCount: 2

image:
  tag: "$API_IMAGE_TAG"

config:
  csm:
    platform:
      argo:
        base-uri: "http://${ARGO_RELEASE_NAME}-argo-workflows-server.${NAMESPACE}.svc.cluster.local:2746"
        workflows:
          namespace: ${NAMESPACE}
          service-account-name: ${ARGO_SERVICE_ACCOUNT}
      twincache:
        host: "cosmotechredis-master.${NAMESPACE}.svc.cluster.local"
        port: "6379"
        username: "default"
        password: "${REDIS_PASSWORD}"


ingress:
  enabled: ${COSMOTECH_API_INGRESS_ENABLED}
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/proxy-body-size: "0"
    nginx.ingress.kubernetes.io/proxy-connect-timeout: "30"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "30"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "30"
    nginx.org/client-max-body-size: "0"
  hosts:
    - host: "${COSMOTECH_API_DNS_NAME}"
  tls:
    - secretName: ${TLS_SECRET_NAME}
      hosts: [${COSMOTECH_API_DNS_NAME}]

resources:
  # Recommended in production environments
  limits:
    #   cpu: 100m
    memory: 2048Mi
  requests:
    #   cpu: 100m
    memory: 1024Mi

nodeSelector:
  cosmotech.com/tier: services

EOF


helm upgrade --install "${COSMOTECH_API_RELEASE_NAME}" "${HELM_CHARTS_BASE_PATH}/helm-chart" \
    --namespace "${NAMESPACE}" \
    --set config.csm.platform.commit-id="$(git rev-parse --short HEAD || "")" \
    --set config.csm.platform.vcs-ref="$(git rev-parse --abbrev-ref HEAD || "")" \
    --set podAnnotations."com\.cosmotech/deployed-at-timestamp"="\"$(date +%s)\"" \
    --values "values-cosmotech-api-deploy.yaml" \
    "${@:5}"

# kube-prometheus-stack
# https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack
# https://artifacthub.io/packages/helm/prometheus-community/kube-prometheus-stack
export MONITORING_NAMESPACE="${NAMESPACE}-monitoring"
kubectl create namespace "${MONITORING_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

MONITORING_NAMESPACE_VAR=${MONITORING_NAMESPACE} \
PROM_STORAGE_CLASS_NAME_VAR=${PROM_STORAGE_CLASS_NAME:-"standard"} \
PROM_STORAGE_RESOURCE_REQUEST_VAR=${PROM_STORAGE_RESOURCE_REQUEST:-"10Gi"} \
PROM_CPU_MEM_LIMITS_VAR=${PROM_CPU_MEM_LIMITS:-"2Gi"} \
PROM_CPU_MEM_REQUESTS_VAR=${PROM_CPU_MEM_REQUESTS:-"2Gi"} \
PROM_REPLICAS_NUMBER_VAR=${PROM_REPLICAS_NUMBER:-"1"} \
PROM_ADMIN_PASSWORD_VAR=${PROM_ADMIN_PASSWORD:-$(date +%s | sha256sum | base64 | head -c 32)} \
envsubst < "${HELM_CHARTS_BASE_PATH}"/kube-prometheus-stack-template.yaml > kube-prometheus-stack.yaml

helm upgrade --install prometheus-operator prometheus-community/kube-prometheus-stack \
             --namespace "${MONITORING_NAMESPACE}" \
             --values "kube-prometheus-stack.yaml"