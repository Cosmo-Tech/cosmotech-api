#!/bin/bash

CURRENT_SCRIPT_DIR=$(realpath "$(dirname "$0")")
HELM_DEPLOY_SCRIPT_BASE_PATH=$(realpath "${CURRENT_SCRIPT_DIR}"/../../api/kubernetes)

PASSWORD_FOR_ARGO_PASSWORD="a-super-secure-password-we-dont-care-about"

# Generate a sample values-ci.yaml. We will also inherit configuration from values-dev.yaml
cat <<EOF > values-ci.yaml
image:
  repository: kind-registry:5000/cosmotech-api

ingress:
  # TODO Ingress disabled for now, but will need to be set once we have an Ingress Controller deployed
  enabled: false

config:
  csm:
    platform:
      azure:
        credentials:
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
  latest \
  --wait \
  --timeout 5m \
  --values "${HELM_DEPLOY_SCRIPT_BASE_PATH}/helm-chart/values-dev.yaml" \
  --values values-ci.yaml

retVal=$?
echo "retVal=$retVal"

echo "=== List all resources across all namespaces ==="
kubectl get all --all-namespaces
echo "=== ==="

echo "=== cosmotech-api Pod logs ==="
COSMOTECH_API_POD=$(kubectl -n "${CHART_RELEASE_TEST_NAMESPACE}" get pods \
  -l "app.kubernetes.io/name=cosmotech-api,app.kubernetes.io/instance=cosmotech-api-latest" \
  -o jsonpath="{.items[0].metadata.name}")
echo "COSMOTECH_API_POD=${COSMOTECH_API_POD}"
kubectl -n "${CHART_RELEASE_TEST_NAMESPACE}" logs "${COSMOTECH_API_POD}"
echo "=== ==="

exit $retVal
