#!/bin/bash

#
# Production version for uninstalling Helm Charts previously installed
#

help() {
  echo
  echo "This script takes at least 2 parameters."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- DELETE_NAMESPACE | boolean (default is true) | whether to delete the provided namespace"
  echo "- DELETE_CERTMANAGER_CLUSTERISSUERS | boolean (default is false) | whether to delete the ClusterIssuer resources, which cluster-scoped resources, not scoped to the provided namespace"
  echo
  echo "Usage: ./$(basename "$0") NAMESPACE API_VERSION [any additional options to pass as is to the Chart uninstallation command]"
  echo
  echo "Examples:"
  echo
  echo "- ./$(basename "$0") my-namespace latest"
  echo
  echo "- ./$(basename "$0") my-namespace v1 --dry-run"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 2 ]]; then
  help
  exit 1
fi

export NAMESPACE="$1"

notice_argo_pvc_pv() {
  echo
  echo "*** NOTE ***"
  echo "You may need to also manually delete any Persistent Volume Claims (PVCs) and Volumes created by Argo!"
  echo "Run 'kubectl -n ${NAMESPACE} get pvc' to list all PVCs in the '${NAMESPACE}' namespace"
  echo "************"
}

API_VERSION="$2"
COSMOTECH_API_RELEASE_NAME="cosmotech-api-${API_VERSION}"
ARGO_RELEASE_NAME=argo
REDIS_RELEASE_NAME=cosmotechredis
REDISINSIGHT_RELEASE_NAME=redisinsight


echo "-> Uninstalling Helm release: '${ARGO_RELEASE_NAME}'..."
helm -n "${NAMESPACE}" uninstall "${ARGO_RELEASE_NAME}" "${@:3}"
argoUninstallReturnValue=$?

echo "-> Uninstalling Helm release: '${COSMOTECH_API_RELEASE_NAME}'..."
helm -n "${NAMESPACE}" uninstall "${COSMOTECH_API_RELEASE_NAME}" "${@:3}"
cosmotechApiUninstallReturnValue=$?

echo "-> Uninstalling Helm release: '${REDIS_RELEASE_NAME}'..."
helm -n "${NAMESPACE}" uninstall "${REDIS_RELEASE_NAME}" "${@:3}"
cosmotechApiUninstallReturnValue=$?

echo "-> Uninstalling Helm release: '${REDISINSIGHT_RELEASE_NAME}'..."
helm -n "${NAMESPACE}" uninstall "${REDISINSIGHT_RELEASE_NAME}" "${@:3}"
cosmotechApiUninstallReturnValue=$?

kubectl -n "${NAMESPACE}" delete secrets \
  custom-tls-secret \
  letsencrypt-prod \
  letsencrypt-prod-private-key \
  letsencrypt-staging \
  letsencrypt-staging-private-key \
  || true

if [[ "${DELETE_CERTMANAGER_CLUSTERISSUERS:-false}" == "true" ]]; then
  kubectl -n "${NAMESPACE}" delete clusterissuers letsencrypt-staging letsencrypt-prod || true
else
  echo "Skipping ClusterIssuers deletion. You may want to remove them afterwards, using the following command: "
  echo "kubectl -n \"${NAMESPACE}\" delete clusterissuers letsencrypt-staging letsencrypt-prod || true"
fi

if [[ "${DELETE_NAMESPACE:-true}" == "true" ]]; then
  kubectl delete namespace "${NAMESPACE}"
else
  echo "Skipping namespace deletion as requested through the DELETE_NAMESPACE environment variable"
fi

if [[ "${argoUninstallReturnValue}" != "0" ]]; then
  echo
  if [[ "${cosmotechApiUninstallReturnValue}" != "0" ]]; then
    echo "/!\ Could not uninstall neither '${ARGO_RELEASE_NAME}' nor '${COSMOTECH_API_RELEASE_NAME}'"
  else
    echo "/!\ Could not uninstall '${ARGO_RELEASE_NAME}'."
  fi
  echo "You will need to proceed manually."
  notice_argo_pvc_pv
  exit $argoUninstallReturnValue
fi

if [[ "${cosmotechApiUninstallReturnValue}" != "0" ]]; then
  echo
  echo "/!\ Could not uninstall '${COSMOTECH_API_RELEASE_NAME}'. You will need to proceed manually."
  notice_argo_pvc_pv
  exit $cosmotechApiUninstallReturnValue
fi

echo "Done."
notice_argo_pvc_pv
