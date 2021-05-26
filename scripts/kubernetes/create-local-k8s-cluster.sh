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
featureGates:
  # TTL Controller for finished resources is currently an opt-in alpha feature
  # https://kubernetes.io/docs/concepts/workloads/controllers/ttlafterfinished/
  TTLAfterFinished: true

EOF

# connect the registry to the cluster network
# (the network may already be connected)
docker network connect "kind" "${registry_name}" || true

kubectl_ctx="kind-${cluster_name}"

# Annotate the cluster node to use the registry
# https://docs.tilt.dev/choosing_clusters.html#discovering-the-registry
for node in $(kind get nodes --name "${cluster_name}"); do
  kubectl --context="${kubectl_ctx}" \
    annotate node "${node}" "kind.x-k8s.io/registry=localhost:${registry_port}";
done

# cf. https://kind.sigs.k8s.io/docs/user/ingress/#ingress-nginx
kubectl --context="${kubectl_ctx}" \
  apply -f \
  https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/static/provider/kind/deploy.yaml
