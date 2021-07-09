#!/bin/bash
usage="./$(basename "$0") {javascript,python,java,csharp} USER RELEASE_NOTE [GIT_TOKEN]"
echo "== Cosmo Tech client library publish script =="
echo "USAGE:"
echo "$usage"
echo "WARNING: If you use Two-Factor Authentication you must export or provide a GIT_TOKEN with a Personal Access Token"
echo "===="
if [ -z "$1" ]
then
  echo "You must provide a client name: javascript, python, java, csharp"
  exit 1
fi
if [ -z "$2" ]
then
  echo "You must provide a github username"
  exit 1
fi
if [ -z "$3" ]
then
  echo "You must provide a release note"
  exit 1
fi
if [ -n "$4" ]; then
  export GIT_TOKEN="$4"
fi
pushd "$(realpath "$(dirname "$0")/../../api/build/generated-sources/$1/scripts")" || exit 1
./git_push.sh "$2" Cosmo-Tech "cosmotech-api-$1-client" "$3" ${ADDITIONAL_GIT_MESSAGES:-}
popd || exit 0
