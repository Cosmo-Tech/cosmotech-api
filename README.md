# Cosmo Tech Cloud Platform API

[![Build, Test and Package](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/build_test_package.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/build_test_package.yml)
[![Lint](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/lint.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/lint.yml)
[![OpenAPI](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi.yml/badge.svg)](https://csmphoenixdev.blob.core.windows.net/public/openapi.yaml)
[![OpenAPI Clients](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi_clients.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi_clients.yml)

## Building

```shell
./gradlew build
```

## Running

### Locally

```shell
java -jar api/build/libs/cosmotech-api-<VERSION>-uberjar.jar
```

You can also run via Spring Boot, like so:
```shell
./gradlew :cosmotech-api:bootRun
```

This activates an `dev` Spring profile, the configuration of which can be overridden through a `config/application-dev.yml` file.

See the [default configuration](api/src/main/resources/application.yml).
Also note that the `azure` profile is also activated by default.

### Kubernetes

#### Azure Kubernetes Service (AKS)

* Login, like so:

```shell
az acr login --name csmphoenix
```

* Build and push the container image, e.g.:

```shell
./gradlew :cosmotech-api:jib \
  -Djib.to.image=csmphoenixdev.azurecr.io/cosmotech-api:latest
```

* Configure the cluster

This assumes you already retrieved the AKS cluster credentials, and configured your
current `kubectl` context accordingly.

Otherwise, run `az aks get-credentials`, e.g.:

```shell
az aks get-credentials \
  --resource-group phoenix \
  --name phoenixAKSdev
```

* Create the namespace if needed

```shell
kubectl create namespace phoenix
```

* Run the deployment script

```
 ./api/kubernetes/deploy_via_helm.sh --help                                                                                                                                       

This script takes at least 4 parameters.

The following optional environment variables can be set to alter this script behavior:
- ARGO_MINIO_REQUESTS_MEMORY | units of bytes (default is 4Gi) | Memory requests for the Argo MinIO server
- NGINX_INGRESS_CONTROLLER_ENABLED | boolean (default is false) | indicating whether an NGINX Ingress Controller should be deployed and an Ingress resource created too
- NGINX_INGRESS_CONTROLLER_REPLICA_COUNT | int (default is 1) | number of pods for the NGINX Ingress Controller
- NGINX_INGRESS_CONTROLLER_LOADBALANCER_IP | IP Address String | optional public IP Address to use as LoadBalancer IP. You can create one with this Azure CLI command: az network public-ip create --resource-group <my-rg>> --name <a-name> --sku Standard --allocation-method static --query publicIp.ipAddress -o tsv 
- NGINX_INGRESS_CONTROLLER_HELM_ADDITIONAL_OPTIONS | Additional Helm options for the NGINX Ingress Controller | Additional options to pass to Helm when creating the Ingress Controller, e.g.: --set controller.service.annotations."service.beta.kubernetes.io/azure-load-balancer-resource-group"=my-azure-resource-group
- CERT_MANAGER_ENABLED  | boolean (default is false). Deprecated - use TLS_CERTIFICATE_TYPE instead | indicating whether cert-manager should be deployed. It is in charge of requesting and managing renewal of Let's Encrypt certificates
- CERT_MANAGER_INSTALL_WAIT_TIMEOUT | string (default is 3m) | how much time to wait for the cert-manager Helm Chart to be successfully deployed
- CERT_MANAGER_USE_ACME_PROD | boolean (default is false) | whether to use the Let's Encrypt Production server. Note that this is subject to rate limiting
- CERT_MANAGER_COSMOTECH_API_DNS_NAME | FQDN String. Deprecated - use COSMOTECH_API_DNS_NAME instead | DNS name, used for Let's Encrypt certificate requests, e.g.: dev.api.cosmotech.com
- COSMOTECH_API_DNS_NAME | FQDN String | DNS name, used for configuring the Ingress resource, e.g.: dev.api.cosmotech.com
- CERT_MANAGER_ACME_CONTACT_EMAIL | Email String. Deprecated - use TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL instead | contact email, used for Let's Encrypt certificate requests
- TLS_CERTIFICATE_TYPE | one of 'none', 'custom', 'let_s_encrypt' | strategy for TLS certificates
- TLS_CERTIFICATE_LET_S_ENCRYPT_CONTACT_EMAIL | Email String | contact email, used for Let's Encrypt certificate requests
- TLS_CERTIFICATE_CUSTOM_CERTIFICATE_PATH | File path | path to a file containing the custom TLS certificate to use for HTTPS
- TLS_CERTIFICATE_CUSTOM_KEY_PATH | File path | path to a file containing the key for the custom TLS certificate to use for HTTPS

Usage: ./deploy_via_helm.sh CHART_PACKAGE_VERSION NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]

Examples:

- ./deploy_via_helm.sh latest phoenix "a-super-secret-password-for-postgresql" latest \
    --values /path/to/my/cosmotech-api-values.yaml \
    --set image.pullPolicy=Always

- ./deploy_via_helm.sh 1.0.1 phoenix "change-me" v1 --values /path/to/my/cosmotech-api-values.yaml
```

You may want to use a dedicated `values.yaml` file instead, like below.
Feel free to copy and customize this [values-azure.yaml](api/kubernetes/helm-chart/values-azure.yaml) file as needed.

```shell
./api/kubernetes/deploy_via_helm.sh latest phoenix "a-secret" latest --values /path/to/my/values-azure-dev.yaml
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

* Run the dev deployment script

** Example

```
./api/kubernetes/deploy_via_helm-dev.sh latest phoenix "a-super-secret-password" latest --values /path/to/my/values-dev.yaml
```

This uses the default [values-dev.yaml](api/kubernetes/helm-chart/values-dev.yaml).

** Usage

```
❯ ./api/kubernetes/deploy_via_helm-dev.sh --help                                                                                         
                                             
This script takes at least 3 parameters.

The following optional environment variables can be set to alter this script behavior:
- ARGO_MINIO_REQUESTS_MEMORY | units of bytes (default is 4Gi) | Memory requests for the Argo MinIO server

Usage: ./deploy_via_helm-dev.sh NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]

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
