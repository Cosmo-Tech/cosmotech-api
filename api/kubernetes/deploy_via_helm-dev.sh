#!/bin/bash

set -euo errexit

export HELM_EXPERIMENTAL_OCI=1

#
# Dev version for deploying the Helm Charts using the local Charts folders
#

help() {
  echo "This script takes at least 3 parameters."
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

NAMESPACE="$1"
API_VERSION="$3"

HELM_CHARTS_BASE_PATH=$(realpath "$(dirname "$0")")

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# Argo
export ARGO_RELEASE_NAME=argo
export ARGO_RELEASE_NAMESPACE="${NAMESPACE}"
export ARGO_POSTGRESQL_PASSWORD="$2"
helm dependency update "${HELM_CHARTS_BASE_PATH}/csm-argo"
envsubst < "${HELM_CHARTS_BASE_PATH}/csm-argo/values.yaml" | \
    helm upgrade --install "${ARGO_RELEASE_NAME}" "${HELM_CHARTS_BASE_PATH}/csm-argo" \
        --namespace "${NAMESPACE}" \
        --values -

# cosmotech-api
COSMOTECH_API_RELEASE_NAME="cosmotech-api-${API_VERSION}"
helm upgrade --install "${COSMOTECH_API_RELEASE_NAME}" "${HELM_CHARTS_BASE_PATH}/helm-chart" \
    --namespace "${NAMESPACE}" \
    "${@:4}" \
    --set api.version="$API_VERSION" \
    --set api.version="$API_VERSION" \
    --set config.csm.platform.argo.base-uri="http://${ARGO_RELEASE_NAME}-server.${NAMESPACE}.svc.cluster.local:2746"
