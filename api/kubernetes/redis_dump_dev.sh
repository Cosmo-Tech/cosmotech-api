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
  echo "  -h: print this help"
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
  echo "Restore Redis database from ${DB_DUMP}"w
  FOUND_POD=$(kubectl get pods -n ${NAMESPACE} | grep ${REDIS_POD} | awk '{print $1}')
  check_not_empty "$FOUND_POD" "Redis pod ${REDIS_POD} not found on namespace ${NAMESPACE}"

  if [ -z "$REDIS_PASSWORD" ]; then
    echo "Redis password not provided, trying to get it from secret"
    get_redis_password
  fi

  RESULT=$(cp ${DB_DUMP} ~/data/dump.rdb)
  check_error "$RESULT" "error" "Error while copying Redis dump: ${RESULT}"

  local redis_connect="redis-cli --no-auth-warning -h ${REDIS_HOST} -p ${REDIS_PORT} -a ${REDIS_PASSWORD}"
  echo "Connecting to Redis: ${redis_connect}"
  RESULT=$(kubectl exec -it ${FOUND_POD} -n ${NAMESPACE} -- ${redis_connect} shutdown nosave)
  check_error "$RESULT" "NOAUTH" "Authentication to Redis failed, wrong password: ${REDIS_PASSWORD}"
  check_error "$RESULT" "Connection refused" "ERROR: ${RESULT}"
  check_error "$RESULT" "ERR" "ERROR: ${RESULT}"
  echo "Redis dump restored from ${DB_DUMP}"
}

get_redis_password() {
  REDIS_PASSWORD=${REDIS_ADMIN_PASSWORD:-$(kubectl get secret --namespace ${NAMESPACE} cosmotechredis -o jsonpath="{.data.redis-password}" | base64 -d || "")}
  check_not_empty "$REDIS_PASSWORD" "Redis password not found in secret"
  echo "Redis password: ${REDIS_PASSWORD}"
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
    h) ACTION=HELP ;;
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
