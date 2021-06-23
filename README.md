# cosmotech-api

> Cosmo Tech Cloud Platform API

[![Build](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/build.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/build.yml)
[![Test](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/test.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/test.yml)
[![Lint](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/lint.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/lint.yml)
[![OpenAPI](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi.yml/badge.svg)](https://csmphoenixdev.blob.core.windows.net/public/openapi.yaml)
[![OpenAPI Clients](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi_clients.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi_clients.yml)

[![Packaging](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/packaging.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/packaging.yml)

## Building

```shell
./gradlew build
```

## Running

### Locally

```shell
java -jar api/build/libs/cosmotech-api-0.0.1-SNAPSHOT-uberjar.jar
```

Spring boot run
```shell
./gradlew :cosmotech-api:bootRun
```
This activate dev profile which uses config/application-dev.yml.

### Kubernetes

#### Azure Kubernetes Service (AKS)

* Login, like so:

```shell
az acr login --name csmphoenix
```

* Build and push the container image, e.g.:

```shell
./gradlew :cosmotech-api:jib \
  -Djib.to.image=csmphoenix.azurecr.io/cosmotech-api:latest
```

* Configure the cluster

This assumes you already retrieved the AKS cluster credentials, and configured your
current `kubectl` context accordingly.

Otherwise, run `az aks get-credentials`, e.g.:

```shell
az aks get-credentials \
  --resource-group phoenix \
  --name phoenixAKS
```

* Create the namespace if needed

```shell
kubectl create namespace phoenix
```

* Install the Helm Chart

This uses [Helm](https://helm.sh/); so make sure you have it installed.

The [api/kubernetes/helm-chart/values-azure.yaml](api/kubernetes/helm-chart/values-azure.yaml) file 
provides sensible defaults for deploying into AKS.
For example, it expects both [cert-manager](https://cert-manager.io/docs/) and 
[NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/) to be available and 
configured in the cluster, since it creates an Ingress Resource configured with TLS support and 
a dedicated hostname: [api.azure.cosmo-platform.com](https://api.azure.cosmo-platform.com).

Head to https://docs.microsoft.com/en-us/azure/aks/ingress-tls for further details.

```shell
export API_VERSION=latest;
helm upgrade --install cosmotech-api-${API_VERSION} \
  api/kubernetes/helm-chart \
  --namespace phoenix \
  --values api/kubernetes/helm-chart/values-azure.yaml \
  --set api.version=$API_VERSION \
  --set image.tag=latest \
  --set imageCredentials.username=`az acr credential show -n csmphoenix --query="username" -o tsv` \
  --set imageCredentials.password=`az acr credential show -n csmphoenix --query="passwords[0].value" -o tsv` \
  --set config.csm.platform.azure.cosmos.uri="<COSMOSDB_HTTPS_ENDPOINT_URI>" \
  --set config.csm.platform.azure.cosmos.key="<COSMOSDB_ACCOUNT_KEY>" \
  --set config.csm.platform.azure.storage.account-name="<AZURE_STORAGE_ACCOUNT>" \
  --set config.csm.platform.azure.storage.account-key="<AZURE_STORAGE_ACCOUNT_ACCESS_KEY>"
```

Alternatively, it is recommended to use a dedicated `values.yaml` file instead, like below.
Feel free to copy and customize this [values-azure.yaml](api/kubernetes/helm-chart/values-azure.yaml) file as needed.

```shell
export API_VERSION=latest;
helm upgrade --install cosmotech-api-${API_VERSION} \
  api/kubernetes/helm-chart \
  --namespace phoenix \
  --values /path/to/my/values-azure.yaml \
  --set api.version=$API_VERSION
```

See the dedicated [README](api/kubernetes/helm-chart/README.md) for more details about the different properties.

#### Local Kubernetes Cluster

* Spawn a local cluster. Skip if you already have configured a local cluster.

Otherwise, you may want to leverage the [scripts/kubernetes/create-local-k8s-cluster.sh](scripts/kubernetes/create-local-k8s-cluster.sh) script,
  which provisions a local [Kind](https://kind.sigs.k8s.io/) cluster, along with a private local container 
registry and an [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/).

To use it, simply [install Kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation), and run the script, like so (`<cluster_name>` is optional and defaults to `local-k8s-cluster`):

```shell
/bin/sh -c scripts/kubernetes/create-local-k8s-cluster.sh [<cluster_name>]
```
This creates a Kubernetes context named `kind-<cluster_name>`.
  
* Build and push the container image to the local registry, e.g.:

```shell
./gradlew :cosmotech-api:jib \
  -Djib.allowInsecureRegistries=true \
  -Djib.to.image=localhost:5000/cosmotech-api:latest
```

* Create the namespace if needed

```shell
kubectl create namespace phoenix
```

* Install the Helm Chart

This uses [Helm](https://helm.sh/); so make sure you have it installed.

```shell
export API_VERSION=latest;
helm upgrade --install cosmotech-api-${API_VERSION} \
  api/kubernetes/helm-chart \
  --namespace phoenix \
  --values api/kubernetes/helm-chart/values-dev.yaml \
  --set api.version=$API_VERSION \
  --set image.tag=latest \
  --set config.csm.platform.azure.cosmos.uri="<COSMOSDB_HTTPS_ENDPOINT_URI>" \
  --set config.csm.platform.azure.cosmos.key="<COSMOSDB_ACCOUNT_KEY>" \
  --set config.csm.platform.azure.storage.account-name="<AZURE_STORAGE_ACCOUNT>" \
  --set config.csm.platform.azure.storage.account-key="<AZURE_STORAGE_ACCOUNT_ACCESS_KEY>"
```

See the dedicated [README](api/kubernetes/helm-chart/README.md) for more details about the different properties.

## Generated items

- [openapi.yaml](https://csmphoenixdev.blob.core.windows.net/public/openapi.yaml)

Some generated items are stored in GitHub:
- Documentation: in [doc/](doc/)
- PlantUml file and image: in [openapi/plantuml](openapi/plantuml)

## Generated API clients
Clients for the API are generated and available on GitHub:
* [Javascript](https://github.com/Cosmo-Tech/cosmotech-api-javascript-client)
* [Python](https://github.com/Cosmo-Tech/cosmotech-api-python-client)
* [Java](https://github.com/Cosmo-Tech/cosmotech-api-java-client)
* [C#](https://github.com/Cosmo-Tech/cosmotech-api-csharp-client)

## License

    Copyright 2021 Cosmo Tech
    
    Permission is hereby granted, free of charge, to any person obtaining a copy of this software 
    and associated documentation files (the "Software"), to deal in the Software without 
    restriction, including without limitation the rights to use, copy, modify, merge, publish, 
    distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the 
    Software is furnished to do so, subject to the following conditions:
    
    The above copyright notice and this permission notice shall be included in all copies or 
    substantial portions of the Software.
    
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING 
    BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND 
    NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
    DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, 
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
