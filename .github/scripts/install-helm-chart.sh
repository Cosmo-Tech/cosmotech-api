#!/bin/bash

API_VERSION=${1:-latest}
IMAGE_TAG=${2:-latest}

CURRENT_SCRIPT_DIR=$(realpath "$(dirname "$0")")
HELM_DEPLOY_SCRIPT_BASE_PATH=$(realpath "${CURRENT_SCRIPT_DIR}"/../../api/kubernetes)

PASSWORD_FOR_ARGO_PASSWORD="a-super-secure-password-we-dont-care-about"

# Generate a sample values-ci.yaml. We will also inherit configuration from values-dev.yaml
cat <<EOF > values-ci.yaml
replicaCount: 1

api:
  version: "${API_VERSION}"
  multiTenant: true
  servletContextPath: /cosmotech-api

server:
  error:
    include-stacktrace: always

# Disable persistence, for now it requires ReadWriteMany capability that is not supported on our kind setup
persistence:
  enabled: false

image:
  repository: localhost:5000/cosmotech-api
  tag: ${IMAGE_TAG}
config:
  spring:
    main:
      allow-bean-definition-overriding: true
  csm:
    platform:
      identityProvider:
        code: okta
        # Use to overwrite openAPI configuration
        authorizationUrl: "https://${IDP_DOMAIN}/oauth2/default/v1/authorize"
        tokenUrl: "https://${IDP_DOMAIN}/oauth2/default/v1/token"
        defaultScopes:
          openid: "OpenId Scope"
        serverBaseUrl: "https://${IDP_DOMAIN}"
        audience: "api://default"
        identity:
          clientId: "my_client_id"
          clientSecret: "my_client_secret"
      authorization:
        mailJwtClaim: "email"
        rolesJwtClaim: "customRoles"
        principalJwtClaim: "email"
        tenantIdJwtClaim: "iss"
        allowed-tenants:
          - "${CHART_RELEASE_TEST_NAMESPACE}"
          - "cosmotech"
      azure:
        credentials:
          core:
            tenantId: "${PHOENIXAKSDEV_TENANT_ID}"
            clientId: "${PHOENIXAKSDEV_CLIENT_ID}"
            clientSecret: "${PHOENIXAKSDEV_CLIENT_SECRET}"
        dataWarehouseCluster:
          baseUri: "${PHOENIXAKSDEV_ADX_BASE_URI}"
          options:
            ingestionUri: "${PHOENIXAKSDEV_ADX_INGESTION_BASE_URI}"
        eventBus:
          baseUri: "${PHOENIXAKSDEV_EVENT_HUBS_BASE_URI}"

EOF

"${HELM_DEPLOY_SCRIPT_BASE_PATH}"/deploy_via_helm-dev.sh \
  "${IMAGE_TAG}" \
  "${CHART_RELEASE_TEST_NAMESPACE}" \
  "${PASSWORD_FOR_ARGO_PASSWORD}" \
  "${API_VERSION}" \
  --wait \
  --timeout 10m \
  --values "${HELM_DEPLOY_SCRIPT_BASE_PATH}/helm-chart/values-dev.yaml" \
  --values values-ci.yaml \

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
