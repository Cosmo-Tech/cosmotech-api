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
export ARGO_POSTGRESQL_PASSWORD="$2"
export API_VERSION="$3"

export ARGO_RELEASE_NAME=argo
export MINIO_RELEASE_NAME=minio
export POSTGRES_RELEASE_NAME=postgres
export ARGO_VERSION="0.16.6"
export MINIO_VERSION="8.0.10"
export POSTGRESQL_VERSION="11.6.12"

export ARGO_DATABASE=argo_workflows
export ARGO_POSTGRESQL_USER=argo
export ARGO_BUCKET_NAME=argo-workflows

HELM_CHARTS_BASE_PATH=$(realpath "$(dirname "$0")")

WORKING_DIR=$(mktemp -d -t cosmotech-api-helm-XXXXXXXXXX)
echo "[info] Working directory: ${WORKING_DIR}"
pushd "${WORKING_DIR}"

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE:-phoenix}" --dry-run=client -o yaml | kubectl apply -f -

# Minio
cat <<EOF > values-minio.yaml
fullnameOverride: argo-${MINIO_RELEASE_NAME}
defaultBucket:
  enabled: true
  name: ${ARGO_BUCKET_NAME}
persistence:
  enabled: true
resources:
  requests:
    memory: "2Gi"
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

EOF

helm repo add bitnami https://charts.bitnami.com/bitnami
helm upgrade --install -n ${NAMESPACE} ${POSTGRES_RELEASE_NAME} bitnami/postgresql --version ${POSTGRESQL_VERSION} --values values-postgresql.yaml

export ARGO_POSTGRESQL_SECRET=argo-postgres-config
cat <<EOF > postgres-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  labels:
    app: postgres
  name: ${ARGO_POSTGRESQL_SECRET}
stringData:
  password: ${ARGO_POSTGRESQL_PASSWORD}
  username: ${ARGO_POSTGRESQL_USER}
type: Opaque

EOF
kubectl apply -n ${NAMESPACE} -f postgres-secret.yaml

# Argo
cat <<EOF > values-argo.yaml
workflow:
  serviceAccount:
    create: true
    name: workflow
  rbac:
    create: true
executor:
  env:
  - name: RESOURCE_STATE_CHECK_INTERVAL
    value: 1s
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
  secure: false
  podLabels:
    networking/traffic-allowed: "yes"
controller:
  podLabels:
    networking/traffic-allowed: "yes"
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
        name: "${ARGO_POSTGRESQL_SECRET}"
        key: username
      passwordSecret:
        name: "${ARGO_POSTGRESQL_SECRET}"
        key: password

EOF

helm repo add argo https://argoproj.github.io/argo-helm
helm upgrade --install -n ${NAMESPACE} ${ARGO_RELEASE_NAME} argo/argo-workflows --version ${ARGO_VERSION} --values values-argo.yaml

popd

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
