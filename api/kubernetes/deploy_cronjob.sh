#!/bin/bash

set -eo errexit

#
# Dev version for deploying the Helm Charts using the local Charts folders
#

help() {
  echo
  echo "This script takes at least 2 parameters."
  echo
  echo "The following optional environment variables can be set to alter this script behavior:"
  echo "- NAMESPACE | string | name of the targeted namespace. Generated when not set"
  echo "- AZURE_DIGITAL_TWINS_URL | string | ex: https://<my-adt-instance>.api.weu.digitaltwins.azure.net"
  echo "- CRON_MINUTES_INTERVAL | string | 5"
  echo
  echo "Usage: ./$(basename "$0") NAMESPACE AZURE_DIGITAL_TWINS_URL CRON_MINUTES_INTERVAL"
}

if [[ "${1:-}" == "--help" ||  "${1:-}" == "-h" ]]; then
  help
  exit 0
fi
if [[ $# -lt 2 ]]; then
  help
  exit 1
fi

VERSION=v4.2.0
BINARY=yq_linux_amd64

export NAMESPACE="$1"
export AZURE_DIGITAL_TWINS_URL="$2"
export CRON_MINUTES_INTERVAL="${3:-5}"
BASE_PATH=$(realpath "$(dirname "$0")")

API_CONFIG_YAML=$(kubectl get secret --namespace ${NAMESPACE} cosmotech-api-latest -o jsonpath="{.data.application-helm\.yml}" | base64 --decode)

if [ -e /usr/bin/yq ]
then
  echo "yq already exists no need to install"
else
  echo "yq do not exist, version to be installed : ${VERSION} , binary ${BINARY}"
  wget https://github.com/mikefarah/yq/releases/download/${VERSION}/${BINARY}.tar.gz -O - | tar xz && sudo mv ${BINARY} /usr/bin/yq
fi

TWIN_CACHE_INFO=$(echo "$API_CONFIG_YAML" | yq e '.csm.platform.twincache' - )
AZURE_CREDENTIALS_INFO=$(echo "$API_CONFIG_YAML" | yq e '.csm.platform.azure.credentials' - )

export TWIN_CACHE_HOST=$( echo "$TWIN_CACHE_INFO" | yq e '.host' - )
export TWIN_CACHE_PORT=$(echo "$TWIN_CACHE_INFO" | yq e '.port' - )
export TWIN_CACHE_PASSWORD=$( echo "$TWIN_CACHE_INFO" | yq e '.password' - )
export AZURE_CLIENT_ID=$( echo "$AZURE_CREDENTIALS_INFO" | yq e '.clientId' - )
export AZURE_TENANT_ID=$( echo "$AZURE_CREDENTIALS_INFO" | yq e '.tenantId' - )
export AZURE_CLIENT_SECRET=$( echo "$AZURE_CREDENTIALS_INFO" | yq e '.clientSecret' - )
export TWIN_CACHE_NAME=$(echo "$AZURE_DIGITAL_TWINS_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||' -e 's|\..*$||')

export CRONJOB_DIR_PATH="$BASE_PATH/cronjob"

mkdir -p "${CRONJOB_DIR_PATH}"

cd "${CRONJOB_DIR_PATH}"

cat <<EOF > adt-sync-cronjob.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: adt-full-sync-cron-${RANDOM}
spec:
  schedule: "*/${CRON_MINUTES_INTERVAL} * * * *"
  concurrencyPolicy: Replace
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          nodeSelector:
            "cosmotech.com/tier": "db"
          tolerations:
            - key: "vendor"
              operator: "Equal"
              value: "cosmotech"
              effect: "NoSchedule"
          containers:
            - name: adt-connector-full-sync-container
              image: ghcr.io/cosmo-tech/adt-twincache-connector:0.0.4
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

rm -rf "${CRONJOB_DIR_PATH}"
