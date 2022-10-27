# cosmotech-api

![Version: 0.0.1](https://img.shields.io/badge/Version-0.0.1-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 1.0.3](https://img.shields.io/badge/AppVersion-1.0.3-informational?style=flat-square)

Cosmo Tech Platform API

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| affinity | object | `{}` | default behavior is a pod anti-affinity, which prevents pods from co-locating on a same node |
| api.servletContextPath | string | `"/"` |  |
| api.version | string | `"latest"` |  |
| argo.imageCredentials.password | string | `""` | password for registry to use for pulling the Workflow images. Useful if you are using a private registry |
| argo.imageCredentials.registry | string | `""` | container registry to use for pulling the Workflow images. Useful if you are using a private registry |
| argo.imageCredentials.username | string | `""` | username for the container registry to use for pulling the Workflow images. Useful if you are using a private registry |
| argo.storage.class | object | `{"install":true,"mountOptions":["dir_mode=0777","file_mode=0777","uid=0","gid=0","mfsymlinks","cache=strict","actimeo=30"],"parameters":{"skuName":"Premium_LRS"},"provisioner":"kubernetes.io/azure-file"}` | storage class used by Workflows submitted to Argo |
| argo.storage.class.install | bool | `true` | whether to install the storage class |
| argo.storage.class.mountOptions | list | `["dir_mode=0777","file_mode=0777","uid=0","gid=0","mfsymlinks","cache=strict","actimeo=30"]` | mount options, depending on the volume plugin configured. If the volume plugin does not support mount options but mount options are specified, provisioning will fail. |
| argo.storage.class.parameters | object | `{"skuName":"Premium_LRS"}` | Parameters describe volumes belonging to the storage class. Different parameters may be accepted depending on the provisioner. |
| argo.storage.class.provisioner | string | `"kubernetes.io/azure-file"` | volume plugin used for provisioning Persistent Volumes |
| autoscaling.enabled | bool | `false` |  |
| autoscaling.maxReplicas | int | `100` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPUUtilizationPercentage | int | `80` |  |
| autoscaling.targetMemoryUtilizationPercentage | int | `80` |  |
| config.csm.platform.argo.base-uri | string | `"http://argo-server:2746"` |  |
| config.csm.platform.argo.workflows.access-modes[0] | string | `"ReadWriteMany"` | Any in the following list: ReadWriteOnce, ReadOnlyMany, ReadWriteMany, ReadWriteOncePod (K8s 1.22+). ReadWriteMany is recommended, as we are likely to have parallel pods accessing the volume |
| config.csm.platform.argo.workflows.requests.storage | string | `"100Gi"` |  |
| config.csm.platform.argo.workflows.storage-class | string | `nil` | Name of the storage class for Workflows volumes. Useful if you want to use a different storage class, installed and managed externally. In this case, you should set argo.storage.class.install to false. |
| config.csm.platform.authorization.allowed-tenants | list | `[]` |  |
| config.csm.platform.azure.containerRegistries.solutions | string | `""` |  |
| config.csm.platform.azure.cosmos.key | string | `"changeme"` | Cosmos DB Database Key. Can be retrieved from the Azure portal |
| config.csm.platform.azure.cosmos.uri | string | `"changeme"` | Cosmos DB Database URI. Can be retrieved from the Azure portal |
| config.csm.platform.azure.credentials.clientId | string | `"changeme"` | Core App Registration Client ID. Deprecated. Use `config.csm.platform.azure.credentials.core.clientId` instead |
| config.csm.platform.azure.credentials.clientSecret | string | `"changeme"` | Core App Registration Client Secret. Deprecated. Use `config.csm.platform.azure.credentials.core.clientSecret` instead |
| config.csm.platform.azure.credentials.customer.clientId | string | `"changeme"` | Customer-provided App Registration Client ID. Workaround for connecting to Azure Digital Twins in the context of a Managed App |
| config.csm.platform.azure.credentials.customer.clientSecret | string | `"changeme"` | Customer-provided App Registration Client Secret. Workaround for connecting to Azure Digital Twins in the context of a Managed App |
| config.csm.platform.azure.credentials.customer.tenantId | string | `"changeme"` | Customer-provided App Registration Tenant ID. Workaround for connecting to Azure Digital Twins in the context of a Managed App |
| config.csm.platform.azure.credentials.tenantId | string | `"changeme"` | Core App Registration Tenant ID. Deprecated. Use `config.csm.platform.azure.credentials.core.tenantId` instead |
| config.csm.platform.azure.dataWarehouseCluster.baseUri | string | `"changeme"` |  |
| config.csm.platform.azure.dataWarehouseCluster.options.ingestionUri | string | `"changeme"` |  |
| config.csm.platform.azure.eventBus.baseUri | string | `"changeme"` |  |
| config.csm.platform.azure.storage.account-key | string | `"changeme"` | Azure storage account access key. Can be retrieved from the Azure portal |
| config.csm.platform.azure.storage.account-name | string | `"changeme"` | Azure storage account name. Length should be between 3 and 24 and use numbers and lower-case letters only |
| config.csm.platform.vendor | string | `"azure"` |  |
| deploymentStrategy | object | `{"rollingUpdate":{"maxSurge":1,"maxUnavailable":"50%"},"type":"RollingUpdate"}` | Deployment strategy |
| deploymentStrategy.rollingUpdate.maxSurge | int | `1` | maximum number of Pods that can be created over the desired number of Pods |
| deploymentStrategy.rollingUpdate.maxUnavailable | string | `"50%"` | maximum number of Pods that can be unavailable during the update process |
| fullnameOverride | string | `""` | value overriding the full name of the Chart. If not set, the value is computed from `nameOverride`. Truncated at 63 chars because some Kubernetes name fields are limited to this. |
| image.pullPolicy | string | `"Always"` | [policy](https://kubernetes.io/docs/concepts/containers/images/#updating-images) for pulling the image |
| image.repository | string | `"ghcr.io/cosmo-tech/cosmotech-api"` | container image to use for deployment |
| image.tag | string | `""` | container image tag. Defaults to the Chart `appVersion` if empty or missing |
| imageCredentials.password | string | `""` | password for registry to use for pulling the Deployment image. Useful if you are using a private registry |
| imageCredentials.registry | string | `""` | container registry to use for pulling the Deployment image. Useful if you are using a private registry |
| imageCredentials.username | string | `""` | username for the container registry to use for pulling the Deployment image. Useful if you are using a private registry |
| ingress.annotations | object | `{}` |  |
| ingress.enabled | bool | `false` |  |
| ingress.hosts[0].host | string | `"chart-example.local"` |  |
| ingress.hosts[0].paths[0].path | string | `"/"` |  |
| ingress.hosts[0].paths[0].pathType | string | `"Prefix"` |  |
| ingress.tls | list | `[]` |  |
| nameOverride | string | `""` | value overriding the name of the Chart. Defaults to the Chart name. Truncated at 63 chars because some Kubernetes name fields are limited to this. |
| nodeSelector | object | `{}` |  |
| podAnnotations | object | `{}` | annotations to set the Deployment pod |
| podSecurityContext | object | `{"runAsNonRoot":true}` | the pod security context, i.e. applicable to all containers part of the pod |
| replicaCount | int | `3` | number of pods replicas |
| resources | object | `{"limits":{"cpu":"1000m","memory":"1024Mi"},"requests":{"cpu":"500m","memory":"512Mi"}}` | resources limits and requests for the pod placement |
| securityContext | object | `{"readOnlyRootFilesystem":true}` | the security context at the pod container level |
| service.managementPort | int | `8081` | service management port |
| service.port | int | `8080` | service port |
| service.type | string | `"ClusterIP"` | service type. See [this page](https://kubernetes.io/docs/concepts/services-networking/service/#publishing-services-service-types) for the possible values |
| serviceAccount.annotations | object | `{}` | annotations to add to the service account |
| serviceAccount.create | bool | `true` | whether a service account should be created |
| serviceAccount.name | string | `""` | the name of the service account to use. If not set and `serviceAccount.create` is `true`, a name is generated using the `fullname` template |
| tolerations | list | `[]` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.5.0](https://github.com/norwoodj/helm-docs/releases/v1.5.0)
