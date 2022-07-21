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
  echo "- REDIS_MASTER_PV | string | The Persistent Volume name for redis master"
  echo "- REDIS_REPLICA1_PV | string | The Persistent Volume name for redis replica 1"
  echo "- REDIS_PVC_STORAGE_CLASS_NAME | string | The Persistent Volume Claim storage class name"
  echo
  echo "Usage: ./$(basename "$0") CHART_PACKAGE_VERSION NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]"
  echo
  echo "Examples:"
  echo
  echo "- ./$(basename "$0") 1.0.1 phoenix \"a-super-secret-password-for-postgresql\" latest \\"
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

echo -- Cosmo Tech Platform installation START

export HELM_EXPERIMENTAL_OCI=1

CHART_PACKAGE_VERSION="$1"
export NAMESPACE="$2"
export API_VERSION="$4"

export VERSION_INGRESS_NGINX="4.1.4"
export VERSION_CERT_MANAGER="1.8.2"
export VERSION_REDIS="16.13.0"
export VERSION_REDIS_COSMOTECH="1.0.0"
export VERSION_REDIS_INSIGHT="0.1.0"

WORKING_DIR=$(mktemp -d -t cosmotech-api-helm-XXXXXXXXXX)
echo -- "[info] Working directory: ${WORKING_DIR}"
cd "${WORKING_DIR}"

# Create namespaces if it does not exist
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

if [[ "${COSMOTECH_API_DNS_NAME:-}" == "" ]]; then
  export COSMOTECH_API_DNS_NAME="${CERT_MANAGER_COSMOTECH_API_DNS_NAME:-}"
fi

# NGINX Ingress Controller & Certificate
echo -- Certificate config
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

echo -- NGINX Ingress Controller
if [[ "${NGINX_INGRESS_CONTROLLER_ENABLED:-false}" == "true" ]]; then
  helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
  helm repo update

  export NGINX_INGRESS_CONTROLLER_REPLICA_COUNT="${NGINX_INGRESS_CONTROLLER_REPLICA_COUNT:-1}"
  export NGINX_INGRESS_CONTROLLER_LOADBALANCER_IP="${NGINX_INGRESS_CONTROLLER_LOADBALANCER_IP:-}"

cat <<EOF > values-ingress-nginx.yaml
controller:
  labels:
    "networking/traffic-allowed": "yes"
  podLabels:
    "networking/traffic-allowed": "yes"
  replicaCount: "${NGINX_INGRESS_CONTROLLER_REPLICA_COUNT}"
  nodeSelector:
    "cosmotech.com/tier": "services"
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  service:
    labels:
      "networking/traffic-allowed": "yes"
    loadBalancerIP: "${NGINX_INGRESS_CONTROLLER_LOADBALANCER_IP}"
  extraArgs:
    default-ssl-certificate: "${NAMESPACE}/${TLS_SECRET_NAME}"
  resources:
    requests:
      cpu: 100m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 512Mi

defaultBackend:
  podLabels:
    "networking/traffic-allowed": "yes"
  nodeSelector:
    "cosmotech.com/tier": "services"
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  resources:
    requests:
      cpu: 100m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 512Mi

EOF

  helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace "${NAMESPACE}" \
   --version "${VERSION_INGRESS_NGINX}" \
    --values values-ingress-nginx.yaml \
    ${NGINX_INGRESS_CONTROLLER_HELM_ADDITIONAL_OPTIONS:-}
fi

# cert-manager
echo -- Cert Manager
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
  helm repo update

cat <<EOF > values-cert-manager.yaml
installCRDs: true
tolerations:
- key: "vendor"
  operator: "Equal"
  value: "cosmotech"
  effect: "NoSchedule"
nodeSelector:
  "cosmotech.com/tier": "services"
podLabels:
  "networking/traffic-allowed": "yes"
resources:
  requests:
    cpu: 10m
    memory: 64Mi
  limits:
    cpu: 1000m
    memory: 64Mi
webhook:
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    "cosmotech.com/tier": "services"
  podLabels:
    "networking/traffic-allowed": "yes"
  resources:
    requests:
      cpu: 10m
      memory: 64Mi
    limits:
      cpu: 1000m
      memory: 64Mi
cainjector:
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    "cosmotech.com/tier": "services"
  podLabels:
    "networking/traffic-allowed": "yes"
  resources:
    requests:
      cpu: 10m
      memory: 64Mi
    limits:
      cpu: 1000m
      memory: 64Mi
startupapicheck:
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    "cosmotech.com/tier": "services"
  podLabels:
    "networking/traffic-allowed": "yes"
  resources:
    requests:
      cpu: 10m
      memory: 64Mi
    limits:
      cpu: 1000m
      memory: 64Mi

EOF

  kubectl label namespace "${NAMESPACE}" cert-manager.io/disable-validation=true --overwrite=true
  helm upgrade --install cert-manager jetstack/cert-manager \
    --namespace "${NAMESPACE}" \
    --version "${VERSION_CERT_MANAGER}" \
    --wait \
    --timeout "${CERT_MANAGER_INSTALL_WAIT_TIMEOUT:-3m}" \
    --set installCRDs=true \
    --values values-cert-manager.yaml

  if [[ "${COSMOTECH_API_DNS_NAME:-}" != "" && "${TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL:-}" != "" ]]; then
    # Wait few seconds until the CertManager WebHook pod is ready.
    # Otherwise, we might run into the following issue :
    # Error from server: error when creating "STDIN": conversion webhook for cert-manager.io/v1alpha2,
    # Kind=Certificate failed: Post "https://cert-manager-webhook.${NAMESPACE}.svc:443/convert?timeout=30s"
echo -- Cluster Issuer and Certificate
    sleep 25
cat <<EOF | kubectl --namespace "${NAMESPACE}" apply --validate=false -f -
apiVersion: cert-manager.io/v1
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
                  "networking/traffic-allowed": "yes"
              spec:
                tolerations:
                - key: "vendor"
                  operator: "Equal"
                  value: "cosmotech"
                  effect: "NoSchedule"
                nodeSelector:
                  "cosmotech.com/tier": "services"
                resources:
                  requests:
                    cpu: 10m
                    memory: 64Mi
                  limits:
                    cpu: 1000m
                    memory: 64Mi

---

apiVersion: cert-manager.io/v1
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

# Redis Cluster
echo -- Redis
# Create Persistent Volume manually to retain data in a disk outside the cluster
# Creating of Persistent Volume claims
if [[ "${REDIS_MASTER_PV:-}" != "" && "${REDIS_REPLICA1_PV:-}" != "" ]]; then
echo Creating Persistent Volume Claim for defined Persistent Volumes for Redis
export REDIS_MASTER_PVC=pvc-redis-master
export REDIS_REPLICA1_PVC=pvc-redis-replica1

cat <<EOF > redis-pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: "${REDIS_MASTER_PVC}"
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: "64Gi"
  volumeName: "${REDIS_MASTER_PV}"
  storageClassName: "${REDIS_PVC_STORAGE_CLASS_NAME:-default}"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: "${REDIS_REPLICA1_PVC}"
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: "64Gi"
  volumeName: "${REDIS_REPLICA1_PV}"
  storageClassName: "${REDIS_PVC_STORAGE_CLASS_NAME:-default}"

EOF
kubectl --namespace "${NAMESPACE}" apply -f redis-pvc.yaml --dry-run=client -o yaml | kubectl apply -f -
fi

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
cat <<EOF > values-redis.yaml
image:
  registry: ghcr.io
  repository: cosmo-tech/cosmotech-redis
  tag: ${VERSION_REDIS_COSMOTECH}
master:
  persistence:
    existingClaim: "${REDIS_MASTER_PVC:-}"
    size: "64Gi"
  podLabels:
    "networking/traffic-allowed": "yes"
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    cosmotech.com/tier: "db"
  resources:
    requests:
      cpu: 500m
      memory: 4Gi
    limits:
      cpu: 1000m
      memory: 4Gi
replica:
  replicaCount: 1
  persistence:
    existingClaim: "${REDIS_REPLICA1_PVC:-}"
    size: "64Gi"
  podLabels:
    "networking/traffic-allowed": "yes"
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    "cosmotech.com/tier": "db"
  resources:
    requests:
      cpu: 500m
      memory: 4Gi
    limits:
      cpu: 1000m
      memory: 4Gi

EOF


helm upgrade --install \
    --namespace ${NAMESPACE} cosmotechredis bitnami/redis \
    --version "${VERSION_REDIS}" \
    --values https://raw.githubusercontent.com/Cosmo-Tech/cosmotech-redis/main/values-cosmotech-cluster.yaml \
    --values values-redis.yaml \
    --wait \
    --timeout 10m0s

# Redis Insight
echo -- Redis Insight
REDIS_INSIGHT_HELM_CHART="redisinsight-chart.tgz"
wget https://docs.redis.com/latest/pkgs/redisinsight-chart-${VERSION_REDIS_INSIGHT}.tgz  -O ${REDIS_INSIGHT_HELM_CHART}

cat <<EOF > values-redis-insight.yaml
service:
  type: NodePort
  port: 80
tolerations:
- key: "vendor"
  operator: "Equal"
  value: "cosmotech"
  effect: "NoSchedule"
nodeSelector:
  cosmotech.com/tier: "services"
resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 1000m
    memory: 128Mi

EOF

helm upgrade --install \
   --namespace ${NAMESPACE} redisinsight ${REDIS_INSIGHT_HELM_CHART} \
   --values values-redis-insight.yaml \
   --wait \
   --timeout 5m0s

# Argo
echo -- Cosmo Tech Argo
export ARGO_RELEASE_NAME=argo
export ARGO_RELEASE_NAMESPACE="${NAMESPACE}"
export ARGO_POSTGRESQL_PASSWORD="$3"
helm pull oci://ghcr.io/cosmo-tech/csm-argo-chart --version "${CHART_PACKAGE_VERSION}" --untar
# Default memory request in MinIO Chart is 4Gi, which may not work in clusters with lower resources
# memory: "${ARGO_MINIO_REQUESTS_MEMORY:-4Gi}"
cat <<EOF > values-csm-argo.yaml
argo:
  server:
    tolerations:
    - key: "vendor"
      operator: "Equal"
      value: "cosmotech"
      effect: "NoSchedule"
    nodeSelector:
      cosmotech.com/tier: "services"
    resources:
      requests:
        cpu: 100m
        memory: 64Mi
      limits:
        cpu: 1000m
        memory: 64Mi
  controller:
    tolerations:
    - key: "vendor"
      operator: "Equal"
      value: "cosmotech"
      effect: "NoSchedule"
    nodeSelector:
      "cosmotech.com/tier": "services"
    resources:
      requests:
        cpu: 100m
        memory: 64Mi
      limits:
        cpu: 1000m
        memory: 64Mi
  minio:
    tolerations:
    - key: "vendor"
      operator: "Equal"
      value: "cosmotech"
      effect: "NoSchedule"
    nodeSelector:
      "cosmotech.com/tier": "services"
    resources:
      requests:
        cpu: 100m
        memory: 256Mi
      limits:
        cpu: 1000m
        memory: 256Mi
    persistence:
      size: 16Gi
    accessKey: "${ARGO_MINIO_ACCESS_KEY:-}"
    secretKey: "${ARGO_MINIO_SECRET_KEY:-}"
postgresql:
  resources:
    requests:
      cpu: 250m
      memory: 256Mi
    limits:
      cpu: 1000m
      memory: 256Mi
  primary:
    tolerations:
    - key: "vendor"
      operator: "Equal"
      value: "cosmotech"
      effect: "NoSchedule"
    nodeSelector:
      "cosmotech.com/tier": "db"

EOF

envsubst < ./csm-argo/values.yaml | \
    helm upgrade --install "${ARGO_RELEASE_NAME}" ./csm-argo \
        --namespace "${NAMESPACE}" \
        --values - \
        --values values-csm-argo.yaml

# cosmotech-api
echo -- Cosmo Tech API
export COSMOTECH_API_RELEASE_NAME="cosmotech-api-${API_VERSION}"
helm pull oci://ghcr.io/cosmo-tech/cosmotech-api-chart --version "${CHART_PACKAGE_VERSION}"

if [[ "${COSMOTECH_API_DNS_NAME:-}" != "" && "${CERT_MANAGER_ACME:-}" != "" ]]; then
  export COSMOTECH_API_INGRESS_ENABLED=true
else
  export COSMOTECH_API_INGRESS_ENABLED=false
fi
cat <<EOF > values-cosmotech-api-deploy.yaml
replicaCount: 2

image:
  tag: "$CHART_PACKAGE_VERSION"

api:
  version: "$API_VERSION"

config:
  csm:
    platform:
      argo:
        base-uri: "http://${ARGO_RELEASE_NAME}-server.${NAMESPACE}.svc.cluster.local:2746"
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

tolerations:
- key: "vendor"
  operator: "Equal"
  value: "cosmotech"
  effect: "NoSchedule"
nodeSelector:
  "cosmotech.com/tier": "services"
resources:
  requests:
    cpu: 500m
    memory: 1024Mi
  limits:
    cpu: 2
    memory: 1024Mi

EOF

if [[ "${CERT_MANAGER_ENABLED:-false}" == "true" ]]; then
  export CERT_MANAGER_INGRESS_ANNOTATION_SET="--set ingress.annotations.cert-manager\.io/cluster-issuer=letsencrypt-${CERT_MANAGER_ACME}"
else
  export CERT_MANAGER_INGRESS_ANNOTATION_SET=""
fi

helm upgrade --install "${COSMOTECH_API_RELEASE_NAME}" "cosmotech-api-chart-${CHART_PACKAGE_VERSION}.tgz" \
    --namespace "${NAMESPACE}" \
    --values values-cosmotech-api-deploy.yaml \
    ${CERT_MANAGER_INGRESS_ANNOTATION_SET} \
    "${@:5}"
echo -- Cosmo Tech Platform installation DONE
