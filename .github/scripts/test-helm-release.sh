#!/bin/bash

helm -n "${CHART_RELEASE_TEST_NAMESPACE}" test cosmotech-api-latest

retVal=$?
echo "retVal=$retVal"

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
