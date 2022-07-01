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

export NAMESPACE="$1"
export API_VERSION="$3"

HELM_CHARTS_BASE_PATH=$(realpath "$(dirname "$0")")

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE:-phoenix}" --dry-run=client -o yaml | kubectl apply -f -

# Argo
export ARGO_RELEASE_NAME=argo
export ARGO_RELEASE_NAMESPACE="${NAMESPACE}"
export ARGO_POSTGRESQL_PASSWORD="$2"
export ARGO_VERSION="v3.3.7"
wget "https://github.com/argoproj/argo-workflows/releases/download/${ARGO_VERSION}/install.yaml" |  \
sed -i -e 's/namespace: argo/namespace: '${NAMESPACE}'/g' | kubectl apply -n "${NAMESPACE}" -f -

# Minio
export MINIO_RELEASE_NAME=argo-artifacts
export BUCKET_NAME=my-bucket
helm repo add minio https://operator.min.io/
helm install ${MINIO_RELEASE_NAME} minio/minio --namespace argo --set fullnameOverride=argo-${MINIO_RELEASE_NAME} \
  --set defaultBucket.enabled=true --set defaultBucket.name=${BUCKET_NAME} --set persistence.enabled=false \
  --set resources.requests.memory=2Gi --set service.type=ClusterIP 

# Configure Argo and Minio
cat <<EOF | kubectl -n "${NAMESPACE}" apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: workflow-controller-configmap
data:
  persistence: |
    connectionPool:
      maxIdleConns: 100
      maxOpenConns: 0
      connMaxLifetime: 0s
    nodeStatusOffLoad: true
    archive: true
    archiveTTL: 7d
  artifactRepository: |
    s3:
      bucket: my-bucket
      endpoint: ${MINIO_RELEASE_NAME}.${NAMESPACE}.svc.cluster.local:9000
      insecure: true
      # References to kubernetes secrets holding credentials
      accessKeySecret:
        name: ${MINIO_RELEASE_NAME}
        key: accesskey
      secretKeySecret:
        name: ${MINIO_RELEASE_NAME}
        key: secretkey    
EOF


# create role binding for argo resources
cat <<EOF | kubectl -n "${NAMESPACE}" apply -f -
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRoleBinding
metadata:
  name: fabric8-rbac
subjects:
  - kind: ServiceAccount
    # Reference to upper's `metadata.name`
    name: default
    # Reference to upper's `metadata.namespace`
    namespace: ${NAMESPACE}
roleRef:
  kind: ClusterRole
  name: cluster-admin
  apiGroup: rbac.authorization.k8s.io
EOF


# Postgres
export POSTGRES_RELEASE_NAME=postgres
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install -n ${NAMESPACE} ${POSTGRES_RELEASE_NAME} bitnami/postgresql

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
    "${@:4}"
