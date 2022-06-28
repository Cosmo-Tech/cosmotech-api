#!/bin/bash

set -eo errexit

#
# Dev version for deploying the Helm Charts using the local Charts folders
#

help() {
  echo
  echo "This script takes at least 3 parameters."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- NAMESPACE | string | name of the targeted namespace. Generated when not set"
  echo "- AZURE_CLIENT_ID | string "
  echo "- AZURE_TENANT_ID | string "
  echo "- AZURE_CLIENT_SECRET | string "
  echo "- AZURE_DIGITAL_TWINS_URL | string | ex: https://<my-adt-instance>.api.weu.digitaltwins.azure.net"
  echo "- TWIN_CACHE_HOST | string | ex: <my-redis-master-instance>.phoenix.svc.cluster.local"
  echo "- TWIN_CACHE_PORT | string | default to 6379"
  echo "- TWIN_CACHE_NAME | string | will be the name of the key"
  echo "- TWIN_CACHE_PASSWORD | string | password used for twin cache connection"
  echo
  echo "Usage: ./$(basename "$0") NAMESPACE AZURE_CLIENT_ID AZURE_TENANT_ID AZURE_CLIENT_SECRET AZURE_DIGITAL_TWINS_URL TWIN_CACHE_HOST TWIN_CACHE_PORT TWIN_CACHE_NAME TWIN_CACHE_PASSWORD"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 9 ]]; then
  help
  exit 1
fi

export NAMESPACE="$1"
export AZURE_CLIENT_ID="$2"
export AZURE_TENANT_ID="$3"
export AZURE_CLIENT_SECRET="$4"
export AZURE_DIGITAL_TWINS_URL="$5"
export TWIN_CACHE_HOST="$6"
export TWIN_CACHE_PORT="$7"
export TWIN_CACHE_NAME="$8"
export TWIN_CACHE_PASSWORD="$9"

BASE_PATH=$(realpath "$(dirname "$0")")

mkdir -p "$BASE_PATH/cronjob"

cd "$BASE_PATH/cronjob"

cat <<EOF > adt-sync-cronjob.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: adt-full-sync-cron
spec:
  schedule: "*/5 * * * *"
  concurrencyPolicy: Replace
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: adt-connector-full-sync-container
              image: ghcr.io/cosmo-tech/adt-twincache-connector:0.0.2
              imagePullPolicy: IfNotPresent
              command: [ 'python', "main.py" ]
              env:
                - name: AZURE_CLIENT_ID
                  value: "${AZURE_CLIENT_ID}"
                - name: AZURE_TENANT_ID
                  value: "${AZURE_TENANT_ID}"
                - name: AZURE_CLIENT_SECRET
                  value: "${AZURE_CLIENT_SECRET}"
                - name: AZURE_DIGITAL_TWINS_URL
                  value: "${AZURE_DIGITAL_TWINS_URL}"
                - name: TWIN_CACHE_HOST
                  value: "${TWIN_CACHE_HOST}"
                - name: TWIN_CACHE_PORT
                  value: "${TWIN_CACHE_PORT}"
                - name: TWIN_CACHE_NAME
                  value: "${TWIN_CACHE_NAME}"
                - name: TWIN_CACHE_PASSWORD
                  value: "${TWIN_CACHE_PASSWORD}"
          restartPolicy: Never

EOF

kubectl create -n "${NAMESPACE}" -f adt-sync-cronjob.yaml
