kubectl create ns argo
kubectl apply -n argo -f https://raw.githubusercontent.com/argoproj/argo-workflows/stable/manifests/install.yaml
kubectl apply -n argo -f workflow-controller-configmap.yaml
