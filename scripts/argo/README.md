# ARGO LOCAL DEPLOYMENT
## Install argo
`install_argo.sh`

## Install argo cli
`install_argo_cli.sh`

## Patch argo ConfigMap for run time executor
`kubectl -n argo -f workflow-controller-configmap.yaml`

## RBAC
Create service account `workflow` and bind workflow role on namespaces which will run workflows (phoenix here)
`kubectl -n phoenix -f workflow-rbac.yaml`

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

## Web Server
To access the web app server you must bind the port
`port_binding.sh`

The server is available at https://localhost:2746
