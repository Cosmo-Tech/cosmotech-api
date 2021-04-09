#!/bin/bash
usage="./publish_client.sh {javascript,python} USER RELEASE_NOTE [GITHUB_TOKEN]"
echo == Cosmo Tech client library publish script ==
echo USAGE:
echo $usage
echo WARNING: If you use Two-Factor Authentication you must export or provide a GITHUB_TOKEN with a Personal Access Token
echo ====
if [ -z $1 ]
then
  echo You must provide a client name: javascript or python
  exit 1
fi
if [ -z $2 ]
then
  echo You must provide a github username
  exit 1
fi
if [ -z $3 ]
then
  echo You must provide a release note
  exit 1
fi
export GIT_TOKEN=$4
pushd ../../api/build/generated-sources/$1
./git_push.sh $2 Cosmo-Tech cosmotech-api-$1-client $3
popd
