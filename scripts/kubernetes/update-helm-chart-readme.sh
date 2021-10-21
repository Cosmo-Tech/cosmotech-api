#!/bin/bash
set -o errexit

for chart in csm-argo helm-chart; do
  docker container run --rm \
    -v "$(realpath "$(dirname "$0")/../../api/kubernetes/${chart}"):/helm-docs" \
    -u "$(id -u)" \
    jnorwood/helm-docs:v1.5.0
done
