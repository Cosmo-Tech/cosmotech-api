#!/bin/bash

set -eo errexit

help() {
  echo
  echo "This script takes at least 2 parameter."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- NAMESPACE | string | name of the targeted namespace. phoenix when not set"
  echo "- PORT_FORWARD | int | port on which expose redisinsight console. 4442 when not set"
  echo
  echo "Usage: ./$(basename "$0") NAMESPACE"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 2 ]]; then
  help
fi

if [[ $# -gt 2 ]]; then
  help
  exit 1
fi

if [ -z "$1" ];
  then
    export NAMESPACE="phoenix"
else
  export NAMESPACE="$1"
fi

if [ -z "$2" ];
  then
    export PORT=4443
else
  export PORT="$2"
fi

echo "Kubernetes Dashboard console: http://localhost:${PORT}"

kubectl --namespace ${NAMESPACE} port-forward service/kubernetes-dashboard ${PORT}:80

