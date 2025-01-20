# cosmotech-api

[![Build, Test and Package](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/build_test_package.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/build_test_package.yml)
[![Lint](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/lint.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/lint.yml)
[![Doc](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/doc.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/doc.yml)

[![OpenAPI](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi.yml/badge.svg)](https://csmphoenixdev.blob.core.windows.net/public/openapi.yaml)
[![OpenAPI Clients](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi_clients.yml/badge.svg)](https://github.com/Cosmo-Tech/cosmotech-api/actions/workflows/openapi_clients.yml)


> AKS releases compatibilities
- latest: 1.22+
- v1: 1.21

> Cosmo Tech Cloud Platform API

The Cosmo Tech Cloud Platform API exposes a [REST](https://en.wikipedia.org/wiki/Representational_state_transfer) API based on [OpenAPI](https://swagger.io/specification/) definitions.

It is written in [Kotlin](https://kotlinlang.org/), makes use of the [Spring Boot framework](https://spring.io/projects/spring-boot), and is built with [Gradle](https://gradle.org/).

## Configuration changes from previous version

### Version 2.0.0

In this version, several functionnalities were added:
- RBAC/ACL functionnality: the possibility to manage rights/permissions for users on Organization/Workspace/Scenario
- TwinCache functionnality: the possibility to cache input dataset in a cache solution
- fine grade customization :
  - Image pull policy
  - claim used for users email and users roles
  - data ingestion (ADX) timeout

You can find the parameters to set in configuration:

```
csm:
  platform:
    ...
    images:
      ...
      imagePullPolicy: "IfNotPresent"
    ...
    authorization:
      ...
      mailJwtClaim: upn
      rolesJwtClaim: roles
    ...
    dataIngestion:
      state:
        noDataTimeOutSeconds: 180
    ...
    twincache:
      host: <twin cache host>
      password: <twin cache password>
      port: "6379"
      username: default
    ...
    rbac:
      enabled: false
```

## Swagger UI

This API is continuously deployed at the following URLs, so you can easily explore it :
- Dev Environment: https://dev.api.cosmotech.com/
- Staging Environment: https://staging.api.cosmotech.com/

## Client Libraries

[![TypeScript](https://img.shields.io/badge/TypeScript-cosmotech--api--typescript--client-brightgreen)](https://github.com/Cosmo-Tech/cosmotech-api-typescript-client)

[![Python](https://img.shields.io/badge/Python-cosmotech--api--python--client-orange)](https://github.com/Cosmo-Tech/cosmotech-api-python-client)

Note that the repositories for all these client libraries are automatically updated and kept in sync, if there is any change in the OpenAPI definition files (in the `main` branch of this repo).

## Building

### Prerequisites

#### JDK

As this project uses both Gradle and Kotlin, a [Java JDK](https://adoptium.net/temurin/releases/?variant=openjdk21&jvmVariant=hotspot) (version 21 or higher) is required.

We recommend installing your JDK with [SDKMAN!](https://sdkman.io/), a tool for managing parallel versions of multiple Software Development Kits on most Unix-based systems.

To check your JDK version, run `java -version` :

```shell
❯ java -version
openjdk 21.0.1 2023-10-17 LTS
OpenJDK Runtime Environment Temurin-21.0.1+12 (build 21.0.1+12-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.1+12 (build 21.0.1+12-LTS, mixed mode, sharing)
```

#### GitHub Packages

This project requires some public dependencies that are stored in GitHub Packages,
which requires users to be authenticated ([even for public repositories](https://github.community/t/download-from-github-package-registry-without-authentication/14407/131)).

You must therefore create a GitHub Personal Access Token (PAT) with the permissions below in order to [work with Maven repositories](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry):
- [read:packages](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages#about-scopes-and-permissions-for-package-registries)

Then add the following lines to your `~/.gradle/gradle.properties` file. Create the file if it does not exist.

```properties
gpr.user=[GITHUB_USERNAME]
gpr.key=[GITHUB_PAT]
```

### Running the build

```shell
./gradlew build -x test -x integrationTest
```

The [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) script takes
care of downloading the Gradle distribution if needed and all dependencies declared in the project. The `-x test -x integrationTest` options are not necessary, they skip the tests, but are helpful if building locally with limited resources.

**Generated items**

The `build` command above generates few items. Some of them are currently versioned to easily access them from this repo. However, there is no need to manually push them. They are automatically pushed if needed, as part of the Continuous Integration runs:

- Documentation: [doc](doc)
- PlantUML file and image: [openapi/plantuml](openapi/plantuml)


## Running locally

A `dev` [Spring Profile](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles) is added to the list of active Profiles.

You will therefore need to specify a `config/application-dev.yml` file, with sensitive configuration like

You may want to copy and customize the [sample configuration](config/application-dev.sample.yml):

```shell
cp config/application-dev.sample.yml config/application-dev.yml
```

Also note that the `keycloak` Profile is activated by default. As such, the [application-keycloak.yml](api/src/main/resources/application-keycloak.yml) file is also read as part of the application configuration.


You can run the application via the `bootRun` Gradle task, like so:

```shell
./gradlew :cosmotech-api:bootRun
```

Once the application is started, you can head to the Swagger UI exposed at http://localhost:8080 to
navigate through the API.

If you use another Identity Provider like Okta, you must set the gradle property IdentityProvider:
```shell
./gradlew :cosmotech-api:bootRun -PidentityProvider=okta
```

If you need to call endpoints that require access to a kubernetes cluster, it will use the current context from your kubernetes local config file.
If you want to use a different context/cluster without changing your default settings, you may pass the `useKubernetesContext` property to the JVM:
```shell
./gradlew :cosmotech-api:bootRun -PjvmArgs=-DuseKubernetesContext=<MY_CONTEXT>
```

## Deploying

This project comes with a set of [Helm](https://helm.sh/) Charts to make it deployable to local or remote Kubernetes clusters.

### Prerequisites

- [kubectl](https://kubernetes.io/docs/tasks/tools/#kubectl)
- [Helm](https://helm.sh/docs/intro/install/)

### Azure Kubernetes Service (AKS)

* Login against the container registry of your choice, like Azure Container Registries in the example below:

```shell
az login
az acr login --name csmenginesdev
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

### Local Kubernetes Cluster

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

**Example**

```
./api/kubernetes/deploy_via_helm-dev.sh phoenix "a-super-secret-password" latest --values /path/to/my/values-dev.yaml
```

This uses the default [values-dev.yaml](api/kubernetes/helm-chart/values-dev.yaml).

**Usage**

```
❯ ./api/kubernetes/deploy_via_helm-dev.sh --help

This script takes at least 3 parameters.

The following optional environment variables can be set to alter this script behavior:
- ARGO_MINIO_REQUESTS_MEMORY | units of bytes (default is 4Gi) | Memory requests for the Argo MinIO server

Usage: ./deploy_via_helm-dev.sh NAMESPACE ARGO_POSTGRESQL_PASSWORD API_VERSION [any additional options to pass as is to the cosmotech-api Helm Chart]

```

See the dedicated [README](api/kubernetes/helm-chart/README.md) for more details about the different properties.

## Contributing

Feel free to submit pull requests or open issues for bugs or feature requests.

We leverage the following tools to enforce code formatting and for code static analysis:
- [Spotless](https://github.com/diffplug/spotless)
- [Detekt](https://detekt.github.io/detekt/)
- [KubeLinter](https://github.com/stackrox/kube-linter) and [helm lint](https://helm.sh/docs/helm/helm_lint/)

These checks are automatically enforced as part of the continuous integration runs on GitHub.

### Coding Style

Code must comply with the common community standards for Kotlin and Java conventions,
based on the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

You can reformat your changes at any time using the `spotlessApply` Gradle task, like so:
```shell
./gradlew spotlessApply
```

Under the hood, this leverages [ktfmt](https://github.com/facebookincubator/ktfmt) for Kotlin code
and [google-java-format](https://github.com/google/google-java-format) for Java code.

This makes an attempt to reformat the code to meet the style requirements.
So make sure to push any resulting changes.

To check that your changes comply with the coding style, run:
```shell
./gradlew spotlessCheck
```

### Static Code Analysis

[Detekt](https://detekt.github.io/detekt/) helps identify code smells in Kotlin code.
And [KubeLinter](https://github.com/stackrox/kube-linter) does the same in Kubernetes YAML resources and Helm Charts.
Container images built are also scanned for common vulnerabilities (CVEs) and best practices violations, using the [Container Scan Action](https://github.com/Azure/container-scan).

Reports are then uploaded to GitHub Code Scanning, under the Security tab of the repo : https://github.com/Cosmo-Tech/cosmotech-api/security/code-scanning

#### Detekt

To run a local analysis with Detekt, simply run the `detekt` Gradle task:
```shell
./gradlew detekt
```

You will then find the reports for the different sub-projects in the `build/reports/detekt` folder, under different formats: Plain text, HTML, and [SARIF](https://sarifweb.azurewebsites.net/).

#### KubeLinter

To run a local analysis of the Helm Charts maintained in this repo:
- install KubeLinter : https://github.com/stackrox/kube-linter#installing-kubelinter
- Run KubeLinter against the 2 Charts:

```shell
kube-linter --config api/kubernetes/.kube-linter.yaml lint api/kubernetes/helm-chart
```

```shell
kube-linter --config api/kubernetes/.kube-linter.yaml lint api/kubernetes/csm-argo
```

#### Vulnerability report

To generate a report of publicly disclosed vulnerabilities in the dependencies
add your API key for the National Vulnerability Database (https://nvd.nist.gov/)
as a property available to gradle. If you don't have a key get one from
here: https://nvd.nist.gov/developers/request-an-api-key. Add your key in your
`~/.gradle/gradle.properties` file (create the file if it does not exist)

```properties
NVD_API_key=[key]
```

Then run the dependency check task which can take about 10 minutes:

```shell
./gradlew dependencyCheckAggregate
```

an html report will be generated under `/build/reports`

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

### License dependencies
The product is using the following dependencies, under their respective licenses: [License check page](https://github.com/Cosmo-Tech/cosmotech-api/blob/main/doc/licenses/index.html)

