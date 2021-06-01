#!/bin/bash

set -euo errexit

#
# Production version for deploying the Helm Charts from the remote ghcr.io OCI registry
#

help() {
  echo "This script takes at least 2 parameters."
  echo "Usage: ./$(basename "$0") NAMESPACE ARGO_POSTGRESQL_PASSWORD [any additional options to pass as is to the cosmotech-api Helm Chart]"
  echo
  echo "Examples:"
  echo
  echo "- ./$(basename "$0") phoenix \"a-super-secret-password-for-postgresql\" \\"
  echo "    --values /path/to/my/cosmotech-api-values.yaml \\"
  echo "    --set image.pullPolicy=Always"
  echo
  echo "- ./$(basename "$0") phoenix \"change-me\" --values /path/to/my/cosmotech-api-values.yaml"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 2 ]]; then
  help
  exit 1
fi

# shellcheck disable=SC2153
IMAGE_TAG="${IMAGE_TAG__}"
CHART_PACKAGE_VERSION="${IMAGE_TAG}"
# shellcheck disable=SC2153
API_VERSION="${API_VERSION__}"

NAMESPACE="$1"

WORKING_DIR=$(mktemp -d -t cosmotech-api-helm-XXXXXXXXXX)
echo "[info] Working directory: ${WORKING_DIR}"
cd "${WORKING_DIR}"

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# Argo
export ARGO_RELEASE_NAME=argo
export ARGO_RELEASE_NAMESPACE="${NAMESPACE}"
export ARGO_POSTGRESQL_PASSWORD="$2"
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
    "${@:3}" \
    --set image.tag="${IMAGE_TAG}" \
    --set api.version="$API_VERSION" \
    --set config.csm.platform.argo.base-uri="http://${ARGO_RELEASE_NAME}-server.${NAMESPACE}.svc.cluster.local:2746"
