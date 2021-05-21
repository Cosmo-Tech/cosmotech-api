#!/bin/bash

set -euo errexit

#
# Production version for deploying the Helm Charts from the remote ghcr.io OCI registry
#

help() {
  echo "This script takes at least 4 parameters."
  echo "Usage: ./$(basename "$0") CHART_PACKAGE_VERSION NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]"
  echo
  echo "Examples:"
  echo
  echo "- ./$(basename "$0") latest phoenix \"a-super-secret-password-for-postgresql\" latest \\"
  echo "    --values /path/to/my/cosmotech-api-values.yaml \\"
  echo "    --set image.pullPolicy=Always"
  echo
  echo "- ./$(basename "$0") 1.0.1 phoenix \"change-me\" v1 --values /path/to/my/cosmotech-api-values.yaml"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 4 ]]; then
  help
  exit 1
fi

CHART_PACKAGE_VERSION="$1"
NAMESPACE="$2"
API_VERSION="$4"

WORKING_DIR=$(mktemp -d -t cosmotech-api-helm-XXXXXXXXXX)
echo "[info] Working directory: ${WORKING_DIR}"
cd "${WORKING_DIR}"

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# Argo
export ARGO_RELEASE_NAME=argo
export ARGO_RELEASE_NAMESPACE="${NAMESPACE}"
export ARGO_POSTGRESQL_PASSWORD="$3"
helm pull oci://ghcr.io/cosmo-tech/csm-argo-chart --version "${CHART_PACKAGE_VERSION}" --untar
envsubst < ./csm-argo/values.yaml | \
    helm upgrade --install "${ARGO_RELEASE_NAME}" ./csm-argo \
        --namespace "${NAMESPACE}" \
        --values -

# cosmotech-api
export COSMOTECH_API_RELEASE_NAME="cosmotech-api-${API_VERSION}"
helm pull oci://ghcr.io/cosmo-tech/cosmotech-api-chart --version "${CHART_PACKAGE_VERSION}"
helm upgrade --install "${COSMOTECH_API_RELEASE_NAME}" "cosmotech-api-chart-${CHART_PACKAGE_VERSION}.tgz" \
    --namespace "${NAMESPACE}" \
    "${@:5}" \
    --set api.version="$API_VERSION" \
    --set config.csm.platform.argo.base-url="http://${ARGO_RELEASE_NAME}-server.${NAMESPACE}.svc.cluster.local:2746"
