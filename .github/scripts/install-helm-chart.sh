#!/bin/bash

API_VERSION=${1:-latest}
IMAGE_TAG=${2:-latest}

CURRENT_SCRIPT_DIR=$(realpath "$(dirname "$0")")
HELM_DEPLOY_SCRIPT_BASE_PATH=$(realpath "${CURRENT_SCRIPT_DIR}"/../../api/kubernetes)

PASSWORD_FOR_ARGO_PASSWORD="a-super-secure-password-we-dont-care-about"
NAMESPACE="phoenix"

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

config:
  spring:
    security:
      oauth2:
        resource-server:
          jwt:
            issuer-uri: "https://localhost/${NAMESPACE}/auth/realms/cosmotech"
            jwk-set-uri: "http://${NAMESPACE}-keycloak.${NAMESPACE}.svc.cluster.local/${NAMESPACE}/auth/realms/cosmotech/protocol/openid-connect/certs"
            audiences:
              - "account"

image:
  repository: localhost:5000/cosmotech-api
  tag: ${IMAGE_TAG}
config:
  csm:
    platform:
      authorization:
        mailJwtClaim: "email"
        rolesJwtClaim: "customRoles"
        principalJwtClaim: "email"
        tenantIdJwtClaim: "iss"
        allowed-tenants:
          - "${NAMESPACE}"
          - "cosmotech"
      identityProvider:
        code: keycloak
        authorizationUrl: "https://localhost/${NAMESPACE}/auth/realms/cosmotech/protocol/openid-connect/auth"
        tokenUrl: "https://localhost/${NAMESPACE}/auth/realms/cosmotech/protocol/openid-connect/token"
        defaultScopes:
          openid: "OpenId Scope"
          email: "Email Scope"
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
        storage:
          account-name: "${PHOENIXAKSDEV_STORAGE_ACCOUNT_NAME}"
          account-key: "${PHOENIXAKSDEV_STORAGE_ACCOUNT_KEY}"

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
