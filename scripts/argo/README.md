# ARGO LOCAL DEPLOYMENT
## Install argo
`install_argo.sh`

## Install argo cli
`install_argo_cli.sh`

## RBAC & Secrets
Create service account `workflow` and bind workflow role on namespaces which will run workflows (phoenix here)
`kubectl -n phoenix -f workflow-rbac.yaml`
Add minio secrets in namespaces which will run workflows with artifacts (phoenix)
`kubectl -n phoenix -f minio-cred.yaml`

You can now submit workflow by precising the `workflow` service account
``` yaml
...
metadata:
  generateName: hello-world-  # Name of this Workflow
spec:
  serviceAccountName: workflow
  entrypoint: whalesay        # Defines "whalesay" as the "main" template
...
```
Example
`argo submit -f hello.yaml -n phoenix`

## Argo Web Server
To access the web app server you must bind the port
`port_binding_argo.sh`

The server is available at https://localhost:2746

## Minio Web Server
To access the minio web app server you must bind the port
`port_binding_minio.sh`

The server is available at http://localhost:9000
