#!/bin/bash

# The script is used to backup redis data and restore redis data.
# The script connects to the redis deployed in the kubernetes cluster.

usage() {
  echo "usage: $0 [-s] [-r] [-x] [-n namespace] [-d redis_pod] [-h redis_host] [-a redis_password] [-p redis_port] [-f dump_file]"
  echo "  -s: save redis database to dump file"
  echo "  -r: restore redis database from dump file"
  echo "  -x: get redis password from secret"
  echo "  -n: namespace where redis is deployed (default: phoenix)"
  echo "  -d: redis pod name (default: cosmotechredis-master)"
  echo "  -h: redis host (default: localhost)"
  echo "  -a: redis password (default: get from secret)"
  echo "  -p: redis port (default: 6379)"
  echo "  -f: dump file (default: /tmp/dump.rdb)"
  echo "example backuping Redis dump: $0 -s -f /tmp/dump.rdb"
  echo "example restoring Redis dump: $0 -r -f /tmp/dump.rdb"
  echo "example getting Redis password from secret: $0 -x"
  exit 1
}

check_not_empty() {
  if [[ -z $1 ]]; then
      echo $2 && echo "Exit from script" && exit 1
  fi
  return 0
}

check_error() {
  if [[ $1 == *$2* ]]; then
      echo $3 && echo "Exit from script" && exit 1
  fi
  return 0
}

save_database() {
  echo "Saving Redis database to ${DB_DUMP}"
  FOUND_POD=$(kubectl get pods -n ${NAMESPACE} | grep ${REDIS_POD} | awk '{print $1}')
  check_not_empty "$FOUND_POD" "Redis pod ${REDIS_POD} not found on namespace ${NAMESPACE}"

  if [ -z "$REDIS_PASSWORD" ]; then
    echo "Redis password not provided, trying to get it from secret"
    get_redis_password
  fi

  local redis_connect="redis-cli --no-auth-warning -h ${REDIS_HOST} -p ${REDIS_PORT} -a ${REDIS_PASSWORD}"
  echo "Connecting to Redis: ${redis_connect}"
  RESULT=$(kubectl exec -it ${FOUND_POD} -n ${NAMESPACE} -- ${redis_connect} save)
  check_error "$RESULT" "NOAUTH" "Authentication to Redis failed, wrong password: ${REDIS_PASSWORD}"
  check_error "$RESULT" "Connection refused" "ERROR: ${RESULT}"
  check_error "$RESULT" "ERR" "ERROR: ${RESULT}"

  [ -e "${DB_DUMP}" ] && rm -f "${DB_DUMP}"
  RESULT=$(kubectl cp ${NAMESPACE}/${FOUND_POD}:data/dump.rdb ${DB_DUMP})
  check_error "$RESULT" "error" "Error while copying Redis dump: ${RESULT}"
  [ -e "${DB_DUMP}" ] && echo "Redis dump saved to ${DB_DUMP}"
}


restore_database() {

  [ ! -f "${DB_DUMP}" ] && echo "ERROR: Redis dump file not found: ${DB_DUMP}" && exit 1

  echo "Restore Redis database from ${DB_DUMP}"w
  FOUND_POD=$(kubectl get pods -n ${NAMESPACE} | grep ${REDIS_POD} | awk '{print $1}')
  check_not_empty "$FOUND_POD" "Redis pod ${REDIS_POD} not found on namespace ${NAMESPACE}"

  if [ -z "$REDIS_PASSWORD" ]; then
    echo "Redis password not provided, trying to get it from secret"
    get_redis_password
  fi

  # Check if we are in local current context
  CURRENT_CONTEXT=$(kubectl config current-context)
  echo "Current context: ${CURRENT_CONTEXT}"
  if [[ $CURRENT_CONTEXT == kind* ]]; then
    echo "We are in local context: $CURRENT_CONTEXT"
    echo "Redis dump will be restored by simply copying the dump file to the local Redis data folder"
    RESULT=$(sudo cp ${DB_DUMP} ~/data/dump.rdb)
    check_error "$RESULT" "error" "Error while copying Redis dump: ${RESULT}"
    restart_redis
    exit 0
  fi

  read -p "We are in remote context: $CURRENT_CONTEXT. Are you sure to restore the database Redis from ${DB_DUMP}? (yes / no) : " response
  if [[ $response != "yes" ]]; then
    echo "Exit from script"
    exit 0
  fi


  PVC=$(kubectl -n $NAMESPACE describe pod $FOUND_POD | grep ClaimName | awk '{print $2}')
  check_not_empty "$PVC" "Error while getting Redis PVC name: ${PVC}"
  echo "Redis PVC name: ${PVC}"

# Create a pod with the same nodeSelector and tolerations as the Redis pod
cat <<EOF > cosmotech-dump.yaml
apiVersion: v1
kind: Pod
metadata:
  name: cosmotech-dump
spec:
  containers:
  - name: cosmotech-dump
    image: alpine
    command:
    - "tail"
    - "-f"
    - "/dev/null"
    volumeMounts:
    - name: data
      mountPath: /data
  tolerations:
  - key: "vendor"
    operator: "Equal"
    value: "cosmotech"
    effect: "NoSchedule"
  nodeSelector:
    "cosmotech.com/tier": "db"
  volumes:
  - name: data
    persistentVolumeClaim:
      claimName: ${PVC}

EOF

  kubectl apply -n ${NAMESPACE} -f cosmotech-dump.yaml
  RESULT=$(kubectl -n ${NAMESPACE} wait --for=condition=Ready pod/cosmotech-dump --timeout=10s)
  check_error "$RESULT" "error" "Error while waiting for pod cosmotech-dump to be ready: ${RESULT}"

  echo "Copying Redis dump to pod cosmotech-dump /data folder"
  RESULT=$(kubectl cp ${DB_DUMP} ${NAMESPACE}/cosmotech-dump:data/dump.rdb)
  check_error "$RESULT" "error" "Error while copying Redis dump: ${RESULT}"

  echo "Restarting Redis pod ${FOUND_POD}"
  restart_redis

  kubectl delete -n ${NAMESPACE} -f cosmotech-dump.yaml
  [ -f "cosmotech-dump.yaml" ] && rm -f "cosmotech-dump.yaml"
}

get_redis_password() {
  REDIS_PASSWORD=${REDIS_ADMIN_PASSWORD:-$(kubectl get secret --namespace ${NAMESPACE} cosmotechredis -o jsonpath="{.data.redis-password}" | base64 -d || "")}
  check_not_empty "$REDIS_PASSWORD" "Redis password not found in secret"
  echo "Redis password: ${REDIS_PASSWORD}"
}

restart_redis() {
  local redis_connect="redis-cli --no-auth-warning -h ${REDIS_HOST} -p ${REDIS_PORT} -a ${REDIS_PASSWORD}"
  echo "Connecting to Redis: ${redis_connect}"
  RESULT=$(kubectl exec -it ${FOUND_POD} -n ${NAMESPACE} -- ${redis_connect} shutdown nosave)
  check_error "$RESULT" "NOAUTH" "Authentication to Redis failed, wrong password: ${REDIS_PASSWORD}"
  check_error "$RESULT" "Connection refused" "ERROR: ${RESULT}"
  check_error "$RESULT" "ERR" "ERROR: ${RESULT}"
  echo "Redis pod ${FOUND_POD} shutdown"
  echo "Wait for Redis pod ${FOUND_POD} to be ready. It could take a few minutes..."
}

unset ACTION DB_DUMP NAMESPACE REDIS_HOST REDIS_POD REDIS_PORT REDIS_PASSWORD
ACTION="HELP"
DB_DUMP="/tmp/dump.rdb"
NAMESPACE="phoenix"
REDIS_HOST="localhost"
REDIS_POD="cosmotechredis-master"
REDIS_PORT="6379"
REDIS_PASSWORD=""


while getopts 'srxhn:d:h:a:p:f:' args
do
  case $args in
    s) ACTION=SAVE ;;
    r) ACTION=RESTORE ;;
    x) ACTION=PASSWORD ;;
    n) NAMESPACE=$OPTARG ;;
    d) REDIS_POD=$OPTARG ;;
    h) REDIS_HOST=$OPTARG ;;
    a) REDIS_PASSWORD=$OPTARG ;;
    p) REDIS_PORT=$OPTARG ;;
    f) DB_DUMP=$OPTARG ;;
    *) echo usage
       exit 1 ;;
  esac
done

if [ -n "$ACTION" ]; then
  case $ACTION in
    SAVE)    save_database "$OPTARG"    ;;
    RESTORE) restore_database "$OPTARG" ;;
    PASSWORD) get_redis_password "$OPTARG" ;;
    HELP)    usage ;;
  esac
fi
