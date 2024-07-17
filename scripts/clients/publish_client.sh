#!/bin/bash

display_usage() {
  usage="./$(basename "$0") {typescript,python} USER RELEASE_NOTE [GIT_TOKEN]"
  echo "== Cosmo Tech client library publish script =="
  echo "USAGE:"
  echo "$usage"
  echo "WARNING: If you use Two-Factor Authentication you must export or provide a GIT_TOKEN with a Personal Access Token"
  echo "===="
}

if [ -z "$1" ]
then
  echo "You must provide a client name: typescript, python"
  display_usage
  exit 1
fi

if [ -z "$2" ]
then
  echo "You must provide a github username"
  display_usage
  exit 1
fi

if [ -z "$3" ]
then
  echo "You must provide a release note"
  display_usage
  exit 1
fi

if [ -n "$4" ]; then
  export GIT_TOKEN="$4"
fi

# shellcheck disable=SC2086
"$(realpath "$(dirname "$0")/../../api/build/generated-sources/openapi/$1/scripts")"/git_push.sh \
  "$2" \
  Cosmo-Tech \
  "cosmotech-api-$1-client" \
  "$3" \
  ${ADDITIONAL_GIT_MESSAGES:-}
