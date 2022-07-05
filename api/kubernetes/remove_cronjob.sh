#!/bin/bash

set -eo errexit

#
# Dev version for deploying the Helm Charts using the local Charts folders
#

help() {
  echo
  echo "This script takes at least 5 parameters."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- NAMESPACE | string | name of the targeted namespace. Generated when not set"
  echo "- CRONJOB_NAME | string | ex: my-beautiful-cron-job"
  echo
  echo "Usage: ./$(basename "$0") NAMESPACE CRONJOB_NAME"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 2 ]]; then
  help
  exit 1
fi
export NAMESPACE=$1
export CRONJOB_NAME=$2

kubectl delete cronjob -n ${NAMESPACE} ${CRONJOB_NAME}
