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

In this version, several functionalities were added:
- RBAC/ACL functionnality: the possibility to manage rights/permissions for users on Organization/Workspace/Scenario
- TwinCache functionality: the possibility to cache input dataset in a cache solution
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

## Contributing

Feel free to submit pull requests or open issues for bugs or feature requests.

We leverage the following tools to enforce code formatting and for code static analysis:
- [Spotless](https://github.com/diffplug/spotless)
- [Detekt](https://detekt.github.io/detekt/)

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

    Copyright 2025 Cosmo Tech

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

