#!/bin/bash
set -o errexit

docker container run --rm \
  -v "$(realpath "$(dirname "$0")/../../api/kubernetes/csm-argo"):/helm-docs" \
  -u "$(id -u)" \
  jnorwood/helm-docs:latest

docker container run --rm \
  -v "$(realpath "$(dirname "$0")/../../api/kubernetes/helm-chart"):/helm-docs" \
  -u "$(id -u)" \
  jnorwood/helm-docs:latest
