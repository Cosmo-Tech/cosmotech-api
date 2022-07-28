#!/bin/sh
set -o errexit

# create registry container unless it already exists
registry_name='local-registry'
registry_port='5000'
running="$(docker container inspect -f '{{.State.Running}}' "${registry_name}" 2>/dev/null || true)"
if [ "${running}" != 'true' ]; then
  registry_image_tag='2.7.1'
  docker container run \
    -d --restart=always -p "${registry_port}:5000" \
    -e REGISTRY_STORAGE_DELETE_ENABLED="true" \
    --name "${registry_name}" \
    registry:${registry_image_tag}
fi

cluster_name=${1:-local-k8s-cluster}

kindest_node_image_tag='v1.21.2'

cat <<EOF | kind create cluster --name "${cluster_name}" --config=-

kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
-  |-
   [plugins."io.containerd.grpc.v1.cri".containerd]
     disable_snapshot_annotations = true
   [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${registry_port}"]
     endpoint = ["http://${registry_name}:${registry_port}"]
nodes:
    - role: control-plane
      image: kindest/node:${kindest_node_image_tag}
      kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
      extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
    - role: worker
      image: kindest/node:${kindest_node_image_tag}
      kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          taints:
          - key: "vendor"
            value: "cosmotech"
            effect: "NoSchedule"
          kubeletExtraArgs:
            node-labels: "kubernetes.io/os=linux,cosmotech.com/tier=compute,cosmotech.com/size=basic"
    - role: worker
      image: kindest/node:${kindest_node_image_tag}
      kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          taints:
          - key: "vendor"
            value: "cosmotech"
            effect: "NoSchedule"
          kubeletExtraArgs:
            node-labels: "kubernetes.io/os=linux,cosmotech.com/tier=compute,cosmotech.com/size=highcpu"
    - role: worker
      image: kindest/node:${kindest_node_image_tag}
      kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          taints:
          - key: "vendor"
            value: "cosmotech"
            effect: "NoSchedule"
          kubeletExtraArgs:
            node-labels: "kubernetes.io/os=linux,cosmotech.com/tier=compute,cosmotech.com/size=highmemory"
    - role: worker
      image: kindest/node:${kindest_node_image_tag}
      kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          taints:
          - key: "vendor"
            value: "cosmotech"
            effect: "NoSchedule"
          kubeletExtraArgs:
            node-labels: "kubernetes.io/os=linux,cosmotech.com/tier=services"
    - role: worker
      image: kindest/node:${kindest_node_image_tag}
      kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          taints:
          - key: "vendor"
            value: "cosmotech"
            effect: "NoSchedule"
          kubeletExtraArgs:
            node-labels: "kubernetes.io/os=linux,cosmotech.com/tier=services"
    - role: worker
      image: kindest/node:${kindest_node_image_tag}
      kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          taints:
          - key: "vendor"
            value: "cosmotech"
            effect: "NoSchedule"
          kubeletExtraArgs:
            node-labels: "kubernetes.io/os=linux,cosmotech.com/tier=db"
    - role: worker
      image: kindest/node:${kindest_node_image_tag}
      kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          taints:
          - key: "vendor"
            value: "cosmotech"
            effect: "NoSchedule"
          kubeletExtraArgs:
            node-labels: "kubernetes.io/os=linux,cosmotech.com/tier=db"
networking:
  # disable kindnet, which does not support Network Policies
  disableDefaultCNI: true
  # set to Calico's default subnet
  podSubnet: 192.168.0.0/16
featureGates:
  # TTL Controller for finished resources is currently an opt-in alpha feature
  # https://kubernetes.io/docs/concepts/workloads/controllers/ttlafterfinished/
  TTLAfterFinished: true

EOF

# connect the registry to the cluster network
# (the network may already be connected)
docker network connect "kind" "${registry_name}" || true

kubectl_ctx="kind-${cluster_name}"

# Patch CoreDNS ConfigMap and re-start the corresponding Deployment, to fix potential issues around
# DNS resolution at Cosmo Tech
nbReplicas=$(kubectl --context="${kubectl_ctx}" -n kube-system get deployment coredns -o=jsonpath='{.status.replicas}')
kubectl --context="${kubectl_ctx}" -n kube-system get configmap coredns -o yaml \
  | sed 's/\/etc\/resolv\.conf/1\.1\.1\.1 1\.0\.0\.1/g' \
  | kubectl --context="${kubectl_ctx}" -n kube-system replace -f -
kubectl --context="${kubectl_ctx}" -n kube-system scale deployment coredns --replicas=0
sleep 3
kubectl --context="${kubectl_ctx}" -n kube-system scale deployment coredns --replicas="${nbReplicas:-1}"

# Communicate the local registry to external local tools
# https://github.com/kubernetes/enhancements/tree/master/keps/sig-cluster-lifecycle/generic/1755-communicating-a-local-registry
cat <<EOF | kubectl --context="${kubectl_ctx}" apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${registry_port}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF

# Annotate the cluster node to use the registry
# https://docs.tilt.dev/choosing_clusters.html#discovering-the-registry
for node in $(kind get nodes --name "${cluster_name}"); do
  kubectl --context="${kubectl_ctx}" \
    annotate node "${node}" "kind.x-k8s.io/registry=localhost:${registry_port}";
done

# Install Calico
helm repo add projectcalico https://projectcalico.docs.tigera.io/charts
helm --kube-context="${kubectl_ctx}" \
  install calico \
  projectcalico/tigera-operator \
  --version v3.21.2

# cf. https://kind.sigs.k8s.io/docs/user/ingress/#ingress-nginx
ingress_nginx_controller_tag="controller-v0.47.0"
kubectl --context="${kubectl_ctx}" \
  apply -f \
  "https://raw.githubusercontent.com/kubernetes/ingress-nginx/${ingress_nginx_controller_tag}/deploy/static/provider/kind/deploy.yaml"

kubectl --context="${kubectl_ctx}" \
  -n ingress-nginx \
  label pods \
  -l "app.kubernetes.io/instance=ingress-nginx,app.kubernetes.io/component=controller" \
  "networking/traffic-allowed=yes"
kubectl --context="${kubectl_ctx}" \
  -n ingress-nginx \
  label services \
  -l "app.kubernetes.io/instance=ingress-nginx,app.kubernetes.io/component=controller" \
  "networking/traffic-allowed=yes"
