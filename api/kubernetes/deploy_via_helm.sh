#!/bin/bash

set -eo errexit
export HELM_EXPERIMENTAL_OCI=1

#
# Production version for deploying the Helm Charts from the remote ghcr.io OCI registry
#

help() {
  echo
  echo "This script takes at least 4 parameters."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- ARGO_MINIO_ACCESS_KEY | string | AccessKey for MinIO. Generated when not set"
  echo "- ARGO_MINIO_SECRET_KEY | string | SecretKey for MinIO. Generated when not set"
  echo "- ARGO_MINIO_REQUESTS_MEMORY | units of bytes (default is 4Gi) | Memory requests for the Argo MinIO server"
  echo "- ARGO_MINIO_PERSISTENCE_SIZE | units of bytes (default is 500Gi) | Persistence size for the Argo MinIO server"
  echo "- NGINX_INGRESS_CONTROLLER_ENABLED | boolean (default is false) | indicating whether an NGINX Ingress Controller should be deployed and an Ingress resource created too"
  echo "- NGINX_INGRESS_CONTROLLER_REPLICA_COUNT | int (default is 1) | number of pods for the NGINX Ingress Controller"
  echo "- NGINX_INGRESS_CONTROLLER_LOADBALANCER_IP | IP Address String | optional public IP Address to use as LoadBalancer IP. You can create one with this Azure CLI command: az network public-ip create --resource-group <my-rg>> --name <a-name> --sku Standard --allocation-method static --query publicIp.ipAddress -o tsv "
  echo "- NGINX_INGRESS_CONTROLLER_HELM_ADDITIONAL_OPTIONS | Additional Helm options for the NGINX Ingress Controller | Additional options to pass to Helm when creating the Ingress Controller, e.g.: --set controller.service.annotations.\"service.beta.kubernetes.io/azure-load-balancer-resource-group\"=my-azure-resource-group"
  echo "- CERT_MANAGER_ENABLED  | boolean (default is false). Deprecated - use TLS_CERTIFICATE_TYPE instead | indicating whether cert-manager should be deployed. It is in charge of requesting and managing renewal of Let's Encrypt certificates"
  echo "- CERT_MANAGER_INSTALL_WAIT_TIMEOUT | string (default is 3m) | how much time to wait for the cert-manager Helm Chart to be successfully deployed"
  echo "- CERT_MANAGER_USE_ACME_PROD | boolean (default is false) | whether to use the Let's Encrypt Production server. Note that this is subject to rate limiting"
  echo "- CERT_MANAGER_COSMOTECH_API_DNS_NAME | FQDN String. Deprecated - use COSMOTECH_API_DNS_NAME instead | DNS name, used for Let's Encrypt certificate requests, e.g.: dev.api.cosmotech.com"
  echo "- COSMOTECH_API_DNS_NAME | FQDN String | DNS name, used for configuring the Ingress resource, e.g.: dev.api.cosmotech.com"
  echo "- CERT_MANAGER_ACME_CONTACT_EMAIL | Email String. Deprecated - use TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL instead | contact email, used for Let's Encrypt certificate requests"
  echo "- TLS_CERTIFICATE_TYPE | one of 'none', 'custom', 'let_s_encrypt' | strategy for TLS certificates"
  echo "- TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL | Email String | contact email, used for Let's Encrypt certificate requests"
  echo "- TLS_CERTIFICATE_CUSTOM_CERTIFICATE_PATH | File path | path to a file containing the custom TLS certificate to use for HTTPS"
  echo "- TLS_CERTIFICATE_CUSTOM_KEY_PATH | File path | path to a file containing the key for the custom TLS certificate to use for HTTPS"
  echo
  echo "Usage: ./$(basename "$0") CHART_PACKAGE_VERSION NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]"
  echo
  echo "Examples:"
  echo
  echo "- ./$(basename "$0") latest phoenix \"a-super-secret-password-for-postgresql\" latest \\"
  echo "    --values /path/to/my/cosmotech-api-values.yaml \\"
  echo "    --set image.pullPolicy=Always"
  echo
  echo "- ./$(basename "$0") 1.0.1 phoenix \"change-me\" v1 --values /path/to/my/cosmotech-api-values.yaml"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 4 ]]; then
  help
  exit 1
fi

export HELM_EXPERIMENTAL_OCI=1

CHART_PACKAGE_VERSION="$1"
export NAMESPACE="$2"
export API_VERSION="$4"

export ARGO_VERSION="0.16.6"
export ARGO_RELEASE_NAME=argo
export ARGO_RELEASE_NAMESPACE="${NAMESPACE}"
export MINIO_VERSION="8.0.10"
export MINIO_RELEASE_NAME=miniocsmv2
export POSTGRES_RELEASE_NAME=postgrescsmv2
export POSTGRESQL_VERSION="11.6.12"
export ARGO_POSTGRESQL_USER=argo
export ARGO_POSTGRESQL_PASSWORD="$3"

export ARGO_DATABASE=argo_workflows
export ARGO_BUCKET_NAME=argo-workflows

WORKING_DIR=$(mktemp -d -t cosmotech-api-helm-XXXXXXXXXX)
echo "[info] Working directory: ${WORKING_DIR}"
pushd "${WORKING_DIR}"

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

if [[ "${COSMOTECH_API_DNS_NAME:-}" == "" ]]; then
  export COSMOTECH_API_DNS_NAME="${CERT_MANAGER_COSMOTECH_API_DNS_NAME:-}"
fi

# NGINX Ingress Controller & Certificate
if [[ "${CERT_MANAGER_USE_ACME_PROD:-false}" == "true" ]]; then
  export CERT_MANAGER_ACME="prod"
  export CERT_MANAGER_ACME_SERVER="https://acme-v02.api.letsencrypt.org/directory"
else
  export CERT_MANAGER_ACME="staging"
  export CERT_MANAGER_ACME_SERVER="https://acme-staging-v02.api.letsencrypt.org/directory"
fi
if [[ "${TLS_CERTIFICATE_TYPE:-let_s_encrypt}" != "let_s_encrypt" ]]; then
  export CERT_MANAGER_ENABLED="false"
  if [[ "${TLS_CERTIFICATE_TYPE:-}" == "custom" ]]; then
    export TLS_SECRET_NAME="custom-tls-secret"
    kubectl -n "${NAMESPACE}" create secret tls "${TLS_SECRET_NAME}" \
      --cert "${TLS_CERTIFICATE_CUSTOM_CERTIFICATE_PATH}" \
      --key "${TLS_CERTIFICATE_CUSTOM_KEY_PATH}" \
      --dry-run=client \
      -o yaml | kubectl -n "${NAMESPACE}" apply -f -
  fi
else
  export CERT_MANAGER_ENABLED="true"
  export TLS_SECRET_NAME="letsencrypt-${CERT_MANAGER_ACME}"
fi

if [[ "${NGINX_INGRESS_CONTROLLER_ENABLED:-false}" == "true" ]]; then
  helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx

  export NGINX_INGRESS_CONTROLLER_REPLICA_COUNT="${NGINX_INGRESS_CONTROLLER_REPLICA_COUNT:-1}"
  export NGINX_INGRESS_CONTROLLER_LOADBALANCER_IP="${NGINX_INGRESS_CONTROLLER_LOADBALANCER_IP:-}"

cat <<EOF > values-ingress-nginx.yaml
controller:
  labels:
    networking/traffic-allowed: "yes"
  podLabels:
    networking/traffic-allowed: "yes"
  replicaCount: "${NGINX_INGRESS_CONTROLLER_REPLICA_COUNT}"
  nodeSelector:
    beta.kubernetes.io/os: "linux"
  service:
    labels:
      networking/traffic-allowed: "yes"
    loadBalancerIP: "${NGINX_INGRESS_CONTROLLER_LOADBALANCER_IP}"
  extraArgs:
    default-ssl-certificate: "${NAMESPACE}/${TLS_SECRET_NAME}"

defaultBackend:
  podLabels:
    networking/traffic-allowed: "yes"
  nodeSelector:
    "beta.kubernetes.io/os": "linux"

EOF

  helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace "${NAMESPACE}" \
    --version 4.1.0 \
    --values values-ingress-nginx.yaml \
    ${NGINX_INGRESS_CONTROLLER_HELM_ADDITIONAL_OPTIONS:-}
fi

# cert-manager
if [[ "${TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL:-}" == "" ]]; then
  export TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL="${CERT_MANAGER_ACME_CONTACT_EMAIL:-}"
fi
if [[ "${TLS_CERTIFICATE_TYPE:-}" == "" ]]; then
  if [[ "${CERT_MANAGER_ENABLED:-}" == "true" ]]; then
    export TLS_CERTIFICATE_TYPE="let_s_encrypt"
  else
    export TLS_CERTIFICATE_TYPE="none"
  fi
fi
if [[ "${CERT_MANAGER_ENABLED:-false}" == "true" ]]; then
  helm repo add jetstack https://charts.jetstack.io

  kubectl label namespace "${NAMESPACE}" cert-manager.io/disable-validation=true --overwrite=true
  helm upgrade --install cert-manager jetstack/cert-manager \
    --namespace "${NAMESPACE}" \
    --version v1.3.1 \
    --wait \
    --timeout "${CERT_MANAGER_INSTALL_WAIT_TIMEOUT:-3m}" \
    --set installCRDs=true \
    --set nodeSelector."beta\.kubernetes\.io/os"=linux \
    --set podLabels."networking/traffic-allowed"=yes \
    --set webhook.podLabels."networking/traffic-allowed"=yes \
    --set cainjector.podLabels."networking/traffic-allowed"=yes

  if [[ "${COSMOTECH_API_DNS_NAME:-}" != "" && "${TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL:-}" != "" ]]; then
    # Wait few seconds until the CertManager WebHook pod is ready.
    # Otherwise, we might run into the following issue :
    # Error from server: error when creating "STDIN": conversion webhook for cert-manager.io/v1alpha2,
    # Kind=Certificate failed: Post "https://cert-manager-webhook.${NAMESPACE}.svc:443/convert?timeout=30s"
    sleep 25
cat <<EOF | kubectl --namespace "${NAMESPACE}" apply --validate=false -f -
apiVersion: cert-manager.io/v1alpha2
kind: ClusterIssuer
metadata:
  name: letsencrypt-${CERT_MANAGER_ACME}
spec:
  acme:
    server: "${CERT_MANAGER_ACME_SERVER}"
    email: "${TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL}"
    privateKeySecretRef:
      name: letsencrypt-${CERT_MANAGER_ACME}-private-key
    solvers:
      - http01:
          ingress:
            class: nginx
            podTemplate:
              metadata:
                labels:
                  networking/traffic-allowed: "yes"
              spec:
                nodeSelector:
                  "kubernetes.io/os": linux

---

apiVersion: cert-manager.io/v1alpha2
kind: Certificate
metadata:
  name: ${TLS_SECRET_NAME}
spec:
  secretName: ${TLS_SECRET_NAME}
  dnsNames:
    - ${COSMOTECH_API_DNS_NAME}
  acme:
    config:
      - http01:
          ingressClass: nginx
        domains:
          - ${COSMOTECH_API_DNS_NAME}
  issuerRef:
    name: letsencrypt-${CERT_MANAGER_ACME}
    kind: ClusterIssuer
EOF
  fi
fi

# Minio
cat <<EOF > values-minio.yaml
fullnameOverride: argo-${MINIO_RELEASE_NAME}
defaultBucket:
  enabled: true
  name: ${ARGO_BUCKET_NAME}
persistence:
  enabled: true
  size: "${ARGO_MINIO_PERSISTENCE_SIZE:-16Gi}"
resources:
  requests:
    memory: "2Gi"
service:
  type: ClusterIP
DeploymentUpdate:
  # As Minio uses a ReadWriteOnce PVC by default, using a RollingUpdate strategy will not
  # work if the new pod is scheduled on a different node.
  # It is possible to force the replicas scheduling on a same node, but this requires that node
  # to have a minimum of 4Gi additional memory available, which is the default memory request
  # set by Argo.
  type: Recreate
networkPolicy:
  # Enabling networking policy returns the following error: unable to recognize "": no matches for kind "NetworkPolicy" in version "networking.k8s.io/v1beta1"
  # => will use the 'networking/traffic-allowed: "yes"' label instead to allow traffic.
  enabled: false
  allowExternal: true
podLabels:
  networking/traffic-allowed: "yes"

EOF

helm repo add minio https://helm.min.io/
helm upgrade --install ${MINIO_RELEASE_NAME} minio/minio --namespace ${NAMESPACE} --version ${MINIO_VERSION} --values values-minio.yaml

# Postgres
cat <<EOF > values-postgresql.yaml
auth:
  username: "${ARGO_POSTGRESQL_USER}"
  password: "${ARGO_POSTGRESQL_PASSWORD}"
  database: ${ARGO_DATABASE}
primary:
  podLabels:
    "networking/traffic-allowed": "yes"

EOF

helm repo add bitnami https://charts.bitnami.com/bitnami
helm upgrade --install -n ${NAMESPACE} ${POSTGRES_RELEASE_NAME} bitnami/postgresql --version ${POSTGRESQL_VERSION} --values values-postgresql.yaml

export ARGO_POSTGRESQL_SECRET_NAME=argo-postgres-config
cat <<EOF > postgres-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  labels:
    app: postgres
  name: ${ARGO_POSTGRESQL_SECRET_NAME}
stringData:
  password: ${ARGO_POSTGRESQL_PASSWORD}
  username: ${ARGO_POSTGRESQL_USER}
type: Opaque

EOF
kubectl apply -n ${NAMESPACE} -f postgres-secret.yaml

# Argo
cat <<EOF > values-argo.yaml
workflow:
  serviceAccount:
    create: true
    name: workflow
  rbac:
    create: true
executor:
  env:
  - name: RESOURCE_STATE_CHECK_INTERVAL
    value: 1s
artifactRepository:
  archiveLogs: true
  s3:
    bucket: ${ARGO_BUCKET_NAME}
    endpoint: ${MINIO_RELEASE_NAME}.${NAMESPACE}.svc.cluster.local:9000
    insecure: true
    accessKeySecret:
      name: ${MINIO_RELEASE_NAME}
      key: accesskey
    secretKeySecret:
      name: ${MINIO_RELEASE_NAME}
      key: secretkey
server:
  extraArgs:
  - --auth-mode=server
  secure: false
  podLabels:
    networking/traffic-allowed: "yes"
controller:
  podLabels:
    networking/traffic-allowed: "yes"
  containerRuntimeExecutor: k8sapi
  metricsConfig:
    enabled: true
  workflowDefaults:
    spec:
      # make sure workflows do not run forever. Default limit set is 7 days (604800 seconds)
      activeDeadlineSeconds: 604800
      ttlStrategy:
        # keep workflows that succeeded for 1d (86400 seconds).
        # We can still view them since they are archived.
        secondsAfterSuccess: 86400
        # keep workflows that have completed (either successfully or not) for 3d (259200 seconds).
        # We can still view them since they are archived.
        secondsAfterCompletion: 259200
      podGC:
        # Delete pods when workflows are successful.
        # We can still access their logs and artifacts since they are archived.
        # One of "OnPodCompletion", "OnPodSuccess", "OnWorkflowCompletion", "OnWorkflowSuccess"
        strategy: OnWorkflowSuccess
      volumeClaimGC:
        # Delete PVCs when workflows are done. However, due to Kubernetes PVC Protection,
        # such PVCs will just be marked as Terminating, until no pod is using them.
        # Pod deletion (either via the Pod GC strategy or the TTL strategy) will allow to free up
        # attached PVCs.
        # One of "OnWorkflowCompletion", "OnWorkflowSuccess"
        strategy: OnWorkflowCompletion
  persistence:
    archive: true
    postgresql:
      host: "${POSTGRES_RELEASE_NAME}-postgresql"
      database: ${ARGO_DATABASE}
      tableName: workflows
      userNameSecret:
        name: "${ARGO_POSTGRESQL_SECRET_NAME}"
        key: username
      passwordSecret:
        name: "${ARGO_POSTGRESQL_SECRET_NAME}"
        key: password

EOF

helm repo add argo https://argoproj.github.io/argo-helm
helm upgrade --install -n ${NAMESPACE} ${ARGO_RELEASE_NAME} argo/argo-workflows --version ${ARGO_VERSION} --values values-argo.yaml

popd

# cosmotech-api
export COSMOTECH_API_RELEASE_NAME="cosmotech-api-${API_VERSION}"
helm pull oci://ghcr.io/cosmo-tech/cosmotech-api-chart --version "${CHART_PACKAGE_VERSION}"

if [[ "${COSMOTECH_API_DNS_NAME:-}" != "" && "${CERT_MANAGER_ACME:-}" != "" ]]; then
  export COSMOTECH_API_INGRESS_ENABLED=true
else
  export COSMOTECH_API_INGRESS_ENABLED=false
fi
cat <<EOF > values-cosmotech-api-deploy.yaml
api:
  version: "$API_VERSION"

image:
  repository: ghcr.io/cosmo-tech/cosmotech-api
  tag: "$CHART_PACKAGE_VERSION"

config:
  csm:
    platform:
      argo:
        base-uri: "http://${ARGO_RELEASE_NAME}-argo-workflows-server.${NAMESPACE}.svc.cluster.local:2746"
        workflows:
          namespace: ${NAMESPACE}

ingress:
  enabled: ${COSMOTECH_API_INGRESS_ENABLED}
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/proxy-body-size: "0"
    nginx.ingress.kubernetes.io/proxy-connect-timeout: "30"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "30"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "30"
    nginx.org/client-max-body-size: "0"
  hosts:
    - host: "${COSMOTECH_API_DNS_NAME}"
  tls:
    - secretName: ${TLS_SECRET_NAME}
      hosts: [${COSMOTECH_API_DNS_NAME}]

resources:
  # Recommended in production environments
  limits:
    #   cpu: 100m
    memory: 2048Mi
  requests:
    #   cpu: 100m
    memory: 1024Mi

nodeSelector:
  agentpool: basicpool

EOF

if [[ "${CERT_MANAGER_ENABLED:-false}" == "true" ]]; then
  export CERT_MANAGER_INGRESS_ANNOTATION_SET="--set ingress.annotations.cert-manager\.io/cluster-issuer=letsencrypt-${CERT_MANAGER_ACME}"
else
  export CERT_MANAGER_INGRESS_ANNOTATION_SET=""
fi

HELM_CHARTS_BASE_PATH=$(realpath "$(dirname "$0")")

helm upgrade --install "${COSMOTECH_API_RELEASE_NAME}" "${HELM_CHARTS_BASE_PATH}/helm-chart" \
    --namespace "${NAMESPACE}" \
    --version ${CHART_PACKAGE_VERSION} \
    --values values-cosmotech-api-deploy.yaml \
    ${CERT_MANAGER_INGRESS_ANNOTATION_SET} \
    "${@:5}"
