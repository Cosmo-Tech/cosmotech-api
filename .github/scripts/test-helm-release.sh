#!/bin/bash

helm -n "${CHART_RELEASE_TEST_NAMESPACE}" test argo || exit 1

helm -n "${CHART_RELEASE_TEST_NAMESPACE}" test cosmotech-api-latest

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
  echo ">>> Logs for cosmotech-api-latest-test-connection-${test} <<<"
  kubectl -n "${CHART_RELEASE_TEST_NAMESPACE}" logs "cosmotech-api-latest-test-connection-${test}"
  echo "-"
done

if [[ "${retVal}" != "0" ]]; then
  echo "Helm Release testing did not complete successfully: $retVal."
  echo "  Command: helm -n ${CHART_RELEASE_TEST_NAMESPACE} test cosmotech-api-latest"
  exit $retVal
fi

# Also test the access via the Ingress
for route in "/" "/openapi" "/openapi.json" "/openapi.yaml" ; do
  echo "==> Testing the access (/${route}) via the Ingress Resource (controlled by the NGINX Ingress Controller)"
  wget --no-check-certificate \
    --tries 10 \
    -S \
    -O - \
    "https://localhost/cosmotech-api${route}" || exit 1
done
