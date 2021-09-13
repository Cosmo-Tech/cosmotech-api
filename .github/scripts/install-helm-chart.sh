#!/bin/bash

API_VERSION=${1:-latest}
IMAGE_TAG=${2:-latest}

CURRENT_SCRIPT_DIR=$(realpath "$(dirname "$0")")
HELM_DEPLOY_SCRIPT_BASE_PATH=$(realpath "${CURRENT_SCRIPT_DIR}"/../../api/kubernetes)

PASSWORD_FOR_ARGO_PASSWORD="a-super-secure-password-we-dont-care-about"

# Generate a sample values-ci.yaml. We will also inherit configuration from values-dev.yaml
cat <<EOF > values-ci.yaml
replicaCount: 1

image:
  repository: localhost:5000/cosmotech-api

config:
  csm:
    platform:
      azure:
        credentials:
          core:
            tenantId: "${PHOENIXAKSDEV_TENANT_ID}"
            clientId: "${PHOENIXAKSDEV_CLIENT_ID}"
            clientSecret: "${PHOENIXAKSDEV_CLIENT_SECRET}"
        cosmos:
          key: "${PHOENIXAKSDEV_COSMOS_KEY}"
          uri: "${PHOENIXAKSDEV_COSMOS_URI}"
        dataWarehouseCluster:
          baseUri: "https://changeme.kusto.windows.net"
          options:
            ingestionUri: "https://changeme.kusto.windows.net"
        eventBus:
          baseUri: "amqps://changeme.servicebus.windows.net"
        storage:
          account-name: "${PHOENIXAKSDEV_STORAGE_ACCOUNT_NAME}"
          account-key: "${PHOENIXAKSDEV_STORAGE_ACCOUNT_KEY}"

EOF

"${HELM_DEPLOY_SCRIPT_BASE_PATH}"/deploy_via_helm-dev.sh \
  "${CHART_RELEASE_TEST_NAMESPACE}" \
  "${PASSWORD_FOR_ARGO_PASSWORD}" \
  "${API_VERSION}" \
  --wait \
  --timeout 5m \
  --values "${HELM_DEPLOY_SCRIPT_BASE_PATH}/helm-chart/values-dev.yaml" \
  --values values-ci.yaml \
  --set image.tag="${IMAGE_TAG}"

retVal=$?
echo "retVal=$retVal"

echo "=== List all resources across all namespaces ==="
kubectl get all --all-namespaces
echo "=== ==="

echo "=== Get all events across all namespaces ==="
kubectl get events --all-namespaces --sort-by='{.lastTimestamp}'
echo "=== ==="

echo "=== Describe all resources across all namespaces ==="
kubectl describe all --all-namespaces
echo "=== ==="

echo "=== cosmotech-api-${API_VERSION} Pod logs ==="
COSMOTECH_API_POD=$(kubectl -n "${CHART_RELEASE_TEST_NAMESPACE}" get pods \
  -l "app.kubernetes.io/name=cosmotech-api,app.kubernetes.io/instance=cosmotech-api-${API_VERSION}" \
  -o jsonpath="{.items[0].metadata.name}")
echo "COSMOTECH_API_POD=${COSMOTECH_API_POD}"
kubectl -n "${CHART_RELEASE_TEST_NAMESPACE}" describe pod "${COSMOTECH_API_POD}"
kubectl -n "${CHART_RELEASE_TEST_NAMESPACE}" logs "${COSMOTECH_API_POD}"

echo "=== ==="

exit $retVal
