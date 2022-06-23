set -x
export MASTER_ENDPOINT_IP=$1
export MASTER_SERVICE_IP=$2
kubectl get -n phoenix endpoints,services
kubectl exec -i -t dnsutils -- nslookup cosmotechredis-headless.phoenix.svc.cluster.local
kubectl exec -i -t dnsutils -- nslookup cosmotechredis-master-0.cosmotechredis-headless.phoenix.svc.cluster.local
kubectl exec -i -t dnsutils -- wget cosmotechredis-master-0.cosmotechredis-headless.phoenix.svc.cluster.local:6379
kubectl exec -i -t dnsutils -- wget $MASTER_ENDPOINT_IP:6379
kubectl exec -i -t dnsutils -- wget $MASTER_SERVICE_IP:6379
