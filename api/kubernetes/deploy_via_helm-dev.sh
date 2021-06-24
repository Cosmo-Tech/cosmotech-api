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
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

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
        --set argo.minio.resources.requests.memory="${ARGO_MINIO_REQUESTS_MEMORY:-512Mi}"

# cosmotech-api
export COSMOTECH_API_RELEASE_NAME="cosmotech-api-${API_VERSION}"
helm upgrade --install "${COSMOTECH_API_RELEASE_NAME}" "${HELM_CHARTS_BASE_PATH}/helm-chart" \
    --namespace "${NAMESPACE}" \
    --values "${HELM_CHARTS_BASE_PATH}/helm-chart/values-dev.yaml" \
    --set image.tag="$API_VERSION" \
    --set api.version="$API_VERSION" \
    --set config.csm.platform.argo.base-uri="http://${ARGO_RELEASE_NAME}-server.${NAMESPACE}.svc.cluster.local:2746" \
    --set config.csm.platform.commit-id="$(git rev-parse --short HEAD || "")" \
    --set config.csm.platform.vcs-ref="$(git rev-parse --abbrev-ref HEAD || "")" \
    "${@:4}"
