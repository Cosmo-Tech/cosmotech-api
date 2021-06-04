#!/bin/bash

helm -n "${CHART_RELEASE_TEST_NAMESPACE}" test cosmotech-api-latest

retVal=$?
echo "retVal=$retVal"

kubectl -n "${CHART_RELEASE_TEST_NAMESPACE}" logs cosmotech-api-latest-test-connection

exit $retVal
