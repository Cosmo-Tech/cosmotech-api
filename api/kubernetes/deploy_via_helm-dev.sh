#!/bin/bash

set -eo errexit

#
# Dev version for deploying the Helm Charts using the local Charts folders
#

help() {
  echo
  echo "This script takes at least 4 parameters."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- API_IMAGE_TAG | string | V1, V2, V3, latest"
  echo "- NAMESPACE | string | name of the targeted namespace. Generated when not set"
  echo "- ARGO_MINIO_ACCESS_KEY | string | AccessKey for MinIO. Generated when not set"
  echo "- ARGO_MINIO_SECRET_KEY | string | SecretKey for MinIO. Generated when not set"
  echo "- ARGO_VERSION | string | Version for Argo. default 3.4.9"
  echo "- ARGO_CHART_VERSION | string | Version for Argo chart used for Argo. default 0.32.2"
  echo "- ARGO_MINIO_VERSION | string | Version for MinIO in Argo. default 12.1.3"
  echo "- ARGO_POSTGRESQL_VERSION | string | Version for PostgreSQL in Argo. default 12.1.3"
  echo "- ARGO_REQUEUE_TIME | string | Workflow requeue time, 1s by default"
  echo "- ARGO_MINIO_REQUESTS_MEMORY | units of bytes (default is 4Gi) | Memory requests for the Argo MinIO server"
  echo "- LOKI_PERSISTENCE_MEMORY | units of bytes (default is 4Gi) | Memory for persistence of Loki system"
  echo "- LOKI_RETENTION_PERIOD | units of hours (default is 720h) | Loki logs retention period"
  echo "- PROM_STORAGE_CLASS_NAME | storage class name for the prometheus PVC (default is standard)"
  echo "- PROM_STORAGE_RESOURCE_REQUEST | size requested for prometheusPVC (default is 10Gi)"
  echo "- PROM_CPU_MEM_LIMITS | memory size limit for prometheus (default is 2Gi)"
  echo "- PROM_CPU_MEM_REQUESTS | memory size requested for prometheus (default is 2Gi)"
  echo "- PROM_REPLICAS_NUMBER | number of prometheus replicas (default is 1)"
  echo "- PROM_ADMIN_PASSWORD | admin password for grafana (generated if not specified)"
  echo "- REDIS_ADMIN_PASSWORD | admin password for redis (generated if not specified)"
  echo
  echo "Usage: ./$(basename "$0") API_IMAGE_TAG NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 4 ]]; then
  help
  exit 1
fi

export API_IMAGE_TAG="$1"
if [ -z "$2" ];
  then
    export NAMESPACE="phoenix"
else
  export NAMESPACE="$2"
fi
export ARGO_POSTGRESQL_PASSWORD="$3"
export API_VERSION="$4"
export REQUEUE_TIME="${ARGO_REQUEUE_TIME:-1s}"

export ARGO_RELEASE_NAME=argo-${NAMESPACE}
export MINIO_RELEASE_NAME=argo-minio-${NAMESPACE}
export POSTGRES_RELEASE_NAME=argo-postgres-${NAMESPACE}
export ARGO_VERSION_ENV=${ARGO_VERSION:-"3.3.8"}
export ARGO_CHART_VERSION_ENV=${ARGO_CHART_VERSION:-"0.16.6"}
export MINIO_VERSION_ENV=${ARGO_MINIO_VERSION:-"12.1.3"}
export POSTGRESQL_VERSION_ENV=${ARGO_POSTGRESQL_VERSION:-"11.6.12"}
export VERSION_REDIS="19.3.2"
export VERSION_REDIS_COSMOTECH="1.0.12"
export VERSION_REDIS_INSIGHT="0.1.0"
export INGRESS_NGINX_VERSION="4.2.5"
export PROMETHEUS_STACK_VERSION="45.0.0"

export ARGO_DATABASE=argo_workflows
export ARGO_POSTGRESQL_USER=argo
export ARGO_BUCKET_NAME=argo-workflows
export ARGO_SERVICE_ACCOUNT=argo-${NAMESPACE}-service-account

export NAMESPACE_NGINX="ingress-nginx"
export MONITORING_NAMESPACE="cosmotech-monitoring"

HELM_CHARTS_BASE_PATH=$(realpath "$(dirname "$0")")

WORKING_DIR=$(mktemp -d -t cosmotech-api-helm-XXXXXXXXXX)
echo "[info] Working directory: ${WORKING_DIR}"
pushd "${WORKING_DIR}"

# Create namespace if it does not exist
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# common exports
export COSMOTECH_API_RELEASE_NAME="cosmotech-api-${NAMESPACE}-${API_VERSION}"
export REDIS_PORT=6379
REDIS_PASSWORD=${REDIS_ADMIN_PASSWORD:-$(kubectl get secret --namespace ${NAMESPACE} cosmotechredis -o jsonpath="{.data.redis-password}" | base64 -d || "")}
if [[ -z "${REDIS_PASSWORD}" ]] ; then
  REDIS_PASSWORD=$(date +%s | sha256sum | base64 | head -c 32)
fi

PROM_PASSWORD=${PROM_ADMIN_PASSWORD:-$(kubectl get secret --namespace ${MONITORING_NAMESPACE} prometheus-operator-grafana -o jsonpath="{.data.admin-password}" | base64 -d || "")}
if [[ -z "${PROM_PASSWORD}" ]] ; then
  PROM_PASSWORD=$(date +%s | sha256sum | base64 | head -c 32)
fi

IS_MONITORING_ACTIVE=$(kubectl get ns ${MONITORING_NAMESPACE} -o json | jq .status.phase -r)
if [ "$IS_MONITORING_ACTIVE" = "Active" ]; then
    echo "${MONITORING_NAMESPACE} is already in the cluster, there's no need to recreate it and install kube-prometheus-stack"
else
  # kube-prometheus-stack
  # https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack
  # https://artifacthub.io/packages/helm/prometheus-community/kube-prometheus-stack
  kubectl create namespace "${MONITORING_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
  helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
  helm repo update

  curl -sSL "https://raw.githubusercontent.com/Cosmo-Tech/azure-platform-deployment-tools/main/deployment_scripts/v3.0/kube-prometheus-stack-template.yaml" \
       -o "${WORKING_DIR}"/kube-prometheus-stack-template.yaml

  MONITORING_NAMESPACE_VAR=${MONITORING_NAMESPACE} \
  PROM_STORAGE_CLASS_NAME_VAR=${PROM_STORAGE_CLASS_NAME:-"standard"} \
  PROM_STORAGE_RESOURCE_REQUEST_VAR=${PROM_STORAGE_RESOURCE_REQUEST:-"10Gi"} \
  PROM_CPU_MEM_LIMITS_VAR=${PROM_CPU_MEM_LIMITS:-"2Gi"} \
  PROM_CPU_MEM_REQUESTS_VAR=${PROM_CPU_MEM_REQUESTS:-"2Gi"} \
  PROM_REPLICAS_NUMBER_VAR=${PROM_REPLICAS_NUMBER:-"1"} \
  PROM_ADMIN_PASSWORD_VAR=${PROM_PASSWORD} \
  REDIS_ADMIN_PASSWORD_VAR=${REDIS_PASSWORD} \
  REDIS_HOST_VAR=cosmotechredis-master.${NAMESPACE}.svc.cluster.local \
  REDIS_PORT_VAR=${REDIS_PORT} \
  envsubst < "${WORKING_DIR}"/kube-prometheus-stack-template.yaml > "${WORKING_DIR}"/kube-prometheus-stack.yaml

  helm upgrade --install prometheus-operator prometheus-community/kube-prometheus-stack \
               --namespace "${MONITORING_NAMESPACE}" \
               --version ${PROMETHEUS_STACK_VERSION} \
               --values "${WORKING_DIR}/kube-prometheus-stack.yaml"
fi

# nginx
kubectl create namespace "${NAMESPACE_NGINX}" --dry-run=client -o yaml | kubectl apply -f -
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

cat <<EOF > /tmp/values-ingress-nginx.yaml
# https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/hack/manifest-templates/provider/kind/values.yaml
# Kind - https://kind.sigs.k8s.io/docs/user/ingress/
controller:
  metrics:
    enabled: true
    serviceMonitor:
      enabled: true
      namespace: $MONITORING_NAMESPACE
  labels:
    networking/traffic-allowed: "yes"
  podLabels:
    networking/traffic-allowed: "yes"
  admissionWebhooks:
    labels:
      networking/traffic-allowed: "yes"
    patch:
      labels:
        networking/traffic-allowed: "yes"
      tolerations:
        - key: "node-role.kubernetes.io/master"
          operator: "Equal"
          effect: "NoSchedule"
        - key: "node-role.kubernetes.io/control-plane"
          operator: "Equal"
          effect: "NoSchedule"
  updateStrategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
  hostPort:
    enabled: true
  terminationGracePeriodSeconds: 0
  service:
    type: NodePort
    labels:
      networking/traffic-allowed: "yes"
  watchIngressWithoutClass: true

  nodeSelector:
    ingress-ready: "true"
  tolerations:
    - key: "node-role.kubernetes.io/master"
      operator: "Equal"
      effect: "NoSchedule"
    - key: "node-role.kubernetes.io/control-plane"
      operator: "Equal"
      effect: "NoSchedule"

  publishService:
    enabled: false
  extraArgs:
    publish-status-address: localhost
defaultBackend:
  podLabels:
    networking/traffic-allowed: "yes"
  tolerations:
    - key: "node-role.kubernetes.io/master"
      operator: "Equal"
      effect: "NoSchedule"
    - key: "node-role.kubernetes.io/control-plane"
      operator: "Equal"
      effect: "NoSchedule"
EOF

helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ${NAMESPACE_NGINX} \
  --version ${INGRESS_NGINX_VERSION} \
  --values /tmp/values-ingress-nginx.yaml

# Redis Cluster

export REDIS_PV_NAME="redis-persistence-volume-${NAMESPACE}"
export REDIS_PVC_NAME="redis-persistence-volume-claim-${NAMESPACE}"

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

cat <<EOF > redis-pv.yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: ${REDIS_PV_NAME}
spec:
  storageClassName: standard
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: /data/
  persistentVolumeReclaimPolicy: Retain
EOF

kubectl apply -n ${NAMESPACE} -f redis-pv.yaml

cat <<EOF > redis-pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: ${REDIS_PVC_NAME}
spec:
  volumeName: ${REDIS_PV_NAME}
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 100Mi
EOF

kubectl apply -n ${NAMESPACE} -f redis-pvc.yaml

cat <<EOF > values-redis.yaml
auth:
  password: ${REDIS_PASSWORD}
image:
  registry: ghcr.io
  repository: cosmo-tech/cosmotech-redis
  tag: ${VERSION_REDIS_COSMOTECH}
master:
  persistence:
    existingClaim: ${REDIS_PVC_NAME}
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
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1024Mi
replica:
  replicaCount: 1
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
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1024Mi

EOF

helm upgrade --install \
    --namespace ${NAMESPACE} cosmotechredis-${NAMESPACE} bitnami/redis \
    --version "${VERSION_REDIS}" \
    --values https://raw.githubusercontent.com/Cosmo-Tech/cosmotech-redis/main/values/v2/values-cosmotech-cluster.yaml \
    --values values-redis.yaml \
    --wait \
    --timeout 10m0s

# Minio
cat <<EOF > values-minio.yaml
fullnameOverride: ${MINIO_RELEASE_NAME}
defaultBuckets: "${ARGO_BUCKET_NAME}"
persistence:
  enabled: true
resources:
  requests:
    memory: "${ARGO_MINIO_REQUESTS_MEMORY:-2Gi}"
    cpu: "100m"
  limits:
    memory: "${ARGO_MINIO_REQUESTS_MEMORY:-2Gi}"
    cpu: "1"
service:
  type: ClusterIP
podLabels:
  networking/traffic-allowed: "yes"
tolerations:
- key: "vendor"
  operator: "Equal"
  value: "cosmotech"
  effect: "NoSchedule"
nodeSelector:
  "cosmotech.com/tier": "services"
auth:
  rootUser: "${ARGO_MINIO_ACCESS_KEY:-}"
  rootPassword: "${ARGO_MINIO_SECRET_KEY:-}"
metrics:
  # Metrics can not be disabled yet: https://github.com/minio/minio/issues/7493
  serviceMonitor:
    enabled: true
    namespace: $MONITORING_NAMESPACE
    interval: 30s
    scrapeTimeout: 10s
EOF

helm upgrade --install ${MINIO_RELEASE_NAME} bitnami/minio --namespace ${NAMESPACE} --version ${MINIO_VERSION_ENV} --values values-minio.yaml

# Postgres
cat <<EOF > values-postgresql.yaml
auth:
  username: "${ARGO_POSTGRESQL_USER}"
  password: "${ARGO_POSTGRESQL_PASSWORD}"
  database: ${ARGO_DATABASE}
primary:
  podLabels:
    "networking/traffic-allowed": "yes"
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    "cosmotech.com/tier": "db"
readReplicas:
  nodeSelector:
    "cosmotech.com/tier": "db"
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
resources:
  requests:
    memory: "64Mi"
    cpu: "250m"
  limits:
    memory: "256Mi"
    cpu: "1"
metrics:
  enabled: true
  serviceMonitor:
    enabled: true
    namespace: $MONITORING_NAMESPACE
    interval: 30s
    scrapeTimeout: 10s
EOF

helm upgrade --install -n ${NAMESPACE} ${POSTGRES_RELEASE_NAME} bitnami/postgresql --version ${POSTGRESQL_VERSION_ENV} --values values-postgresql.yaml

export ARGO_POSTGRESQL_SECRET_NAME=argo-postgres-config
cat <<EOF > postgres-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  labels:
    app: postgres
  name: ${ARGO_POSTGRESQL_SECRET_NAME}
stringData:
  password: "${ARGO_POSTGRESQL_PASSWORD}"
  username: "${ARGO_POSTGRESQL_USER}"
type: Opaque

EOF
kubectl apply -n ${NAMESPACE} -f postgres-secret.yaml

# Argo
## CRDs

CRD=('argoproj.io_clusterworkflowtemplates.yaml' 'argoproj.io_cronworkflows.yaml' \
'argoproj.io_workflowartifactgctasks.yaml' 'argoproj.io_workfloweventbindings.yaml' \
'argoproj.io_workflows.yaml' 'argoproj.io_workflowtaskresults.yaml' 'argoproj.io_workflowtasksets.yaml' \
'argoproj.io_workflowtemplates.yaml')

for crd in "${CRD[@]}"
do
  echo "Downloading Argo CRDs: https://raw.githubusercontent.com/argoproj/argo-workflows/v${ARGO_VERSION_ENV}/manifests/base/crds/minimal/$crd"
  curl -sSL --fail "https://raw.githubusercontent.com/argoproj/argo-workflows/v${ARGO_VERSION_ENV}/manifests/base/crds/minimal/$crd" -o $crd || true
  if [[ -e "$crd" ]] ; then
    echo "Installing Argo CRDs: $crd"
    kubectl apply -n ${NAMESPACE} -f "$crd"
  fi
done


## Chart
cat <<EOF > values-argo.yaml
singleNamespace: true
createAggregateRoles: false
crds:
  install: false
images:
  pullPolicy: IfNotPresent
workflow:
  serviceAccount:
    create: true
    name: ${ARGO_SERVICE_ACCOUNT}
  rbac:
    create: true
executor:
  env:
  - name: RESOURCE_STATE_CHECK_INTERVAL
    value: 1s
  - name: WAIT_CONTAINER_STATUS_CHECK_INTERVAL
    value: 1s
useDefaultArtifactRepo: true
artifactRepository:
  archiveLogs: true
  s3:
    bucket: ${ARGO_BUCKET_NAME}
    endpoint: ${MINIO_RELEASE_NAME}.${NAMESPACE}.svc.cluster.local:9000
    insecure: true
    accessKeySecret:
      name: ${MINIO_RELEASE_NAME}
      key: root-user
    secretKeySecret:
      name: ${MINIO_RELEASE_NAME}
      key: root-password
server:
  clusterWorkflowTemplates:
    enabled: false
  extraArgs:
  - --auth-mode=server
  secure: false
  podLabels:
    networking/traffic-allowed: "yes"
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    "cosmotech.com/tier": "services"
  resources:
    requests:
      memory: "64Mi"
      cpu: "100m"
    limits:
      memory: "128Mi"
      cpu: "1"
controller:
  extraEnv:
  - name: DEFAULT_REQUEUE_TIME
    value: "${REQUEUE_TIME}"
  extraArgs:
  - "--managed-namespace"
  - "${NAMESPACE}"
  clusterWorkflowTemplates:
    enabled: false
  podLabels:
    networking/traffic-allowed: "yes"
  serviceMonitor:
    enabled: true
    namespace: $MONITORING_NAMESPACE
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    "cosmotech.com/tier": "services"
  resources:
    requests:
      memory: "64Mi"
      cpu: "100m"
    limits:
      memory: "128Mi"
      cpu: "1"
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
mainContainer:
  imagePullPolicy: IfNotPresent

EOF

helm repo add argo https://argoproj.github.io/argo-helm
helm upgrade --install -n ${NAMESPACE} ${ARGO_RELEASE_NAME} argo/argo-workflows --version ${ARGO_CHART_VERSION_ENV} --values values-argo.yaml

LOKI_RELEASE_NAME="loki"
helm repo add grafana https://grafana.github.io/helm-charts

cat <<EOF > loki-values.yaml
loki:
  persistence:
    enabled: true
    accessModes:
    - ReadWriteOnce
    size: "${LOKI_PERSISTENCE_MEMORY:-4Gi}"
  config:
    auth_enabled: true
    table_manager:
      retention_deletes_enabled: true
      retention_period: "${LOKI_RETENTION_PERIOD:-720h}"
grafana:
  enabled: true
  persistence:
    type: pvc
    enabled: true
    # storageClassName: default
    accessModes:
      - ReadWriteOnce
    size: "${LOKI_PERSISTENCE_MEMORY:-4Gi}"
promtail:
  config:
    clients:
      - url: http://loki.${MONITORING_NAMESPACE}:3100/loki/api/v1/push
    snippets:
      pipelineStages:
      - cri: {}
      - match:
          selector: '{namespace="${NAMESPACE}"}'
          stages:
            - json:
                expressions:
                  output: log
            - json:
                source: output
                expressions:
                  tenant_id: ${NAMESPACE}
                  message: message
                  level: log.level
            - labels:
                tenant_id:
                message:
                namespace:
            - tenant:
                value: ${NAMESPACE}
      - output:
          source: message
  tolerations:
    - effect: NoSchedule
      operator: Exists
EOF
helm upgrade --install ${LOKI_RELEASE_NAME} --namespace=${MONITORING_NAMESPACE} grafana/loki-stack -f loki-values.yaml

popd

# cosmotech-api

cat <<EOF > values-cosmotech-api-deploy.yaml
replicaCount: 2
api:
  version: "$API_VERSION"
  multiTenant: ${MULTI_TENANT:-true}
  servletContextPath: /cosmotech-api

image:
  repository: ghcr.io/cosmo-tech/cosmotech-api
  tag: "$API_IMAGE_TAG"

config:
  logging:
    level:
      com.cosmotech: TRACE
      web: TRACE
      org.springframework: TRACE
      com.redis: TRACE
  server:
    error:
      include-stacktrace: always
  csm:
    platform:
      authorization:
        allowed-tenants:
          - "${NAMESPACE}"
          - "cosmotech"
      argo:
        base-uri: "http://${ARGO_RELEASE_NAME}-argo-workflows-server.${NAMESPACE}.svc.cluster.local:2746"
        workflows:
          namespace: ${NAMESPACE}
          service-account-name: ${ARGO_SERVICE_ACCOUNT}
      twincache:
        host: "cosmotechredis-${NAMESPACE}-master.${NAMESPACE}.svc.cluster.local"
        port: ${REDIS_PORT}
        username: "default"
        password: "${REDIS_PASSWORD}"

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
  limits:
    cpu: 1000m
    memory: 1024Mi
  requests:
    cpu: 500m
    memory: 512Mi

tolerations:
- key: "vendor"
  operator: "Equal"
  value: "cosmotech"
  effect: "NoSchedule"

nodeSelector:
  "cosmotech.com/tier": "services"

EOF


helm upgrade --install "${COSMOTECH_API_RELEASE_NAME}" "${HELM_CHARTS_BASE_PATH}/helm-chart" \
    --namespace "${NAMESPACE}" \
    --set podAnnotations."com\.cosmotech/deployed-at-timestamp"="\"$(date +%s)\"" \
    --values "values-cosmotech-api-deploy.yaml" \
    "${@:5}"

