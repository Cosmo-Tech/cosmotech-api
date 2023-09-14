#!/bin/bash

API_VERSION=${1:-latest}

helm -n "${CHART_RELEASE_TEST_NAMESPACE}" test "cosmotech-api-${CHART_RELEASE_TEST_NAMESPACE}-${API_VERSION}"

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

for test in openapi swaggerui; do
  echo ">>> Logs for cosmotech-api-${API_VERSION}-test-connection-${test} <<<"
  kubectl -n "${CHART_RELEASE_TEST_NAMESPACE}" logs "cosmotech-api-${CHART_RELEASE_TEST_NAMESPACE}-${API_VERSION}-test-connection-${test}"
  echo "-"
done

if [[ "${retVal}" != "0" ]]; then
  echo "Helm Release testing did not complete successfully: $retVal."
  echo "  Command: helm -n ${CHART_RELEASE_TEST_NAMESPACE} test cosmotech-api-${CHART_RELEASE_TEST_NAMESPACE}-${API_VERSION}"
  exit $retVal
fi

# Also test the access via the Ingress
if [[ "${API_VERSION}" == "latest" ]]; then
  base_path=""
else
  base_path="/${API_VERSION}"
fi
for route in "/" "/openapi" "/openapi.json" "/openapi.yaml" ; do
  echo "==> Testing the access (${base_path}${route}) via the Ingress Resource (controlled by an Ingress Controller)"
  wget --no-check-certificate \
    --tries 10 \
    -S \
    -O - \
    "https://localhost/cosmotech-api/${CHART_RELEASE_TEST_NAMESPACE}${base_path}${route}" || exit 1
done
