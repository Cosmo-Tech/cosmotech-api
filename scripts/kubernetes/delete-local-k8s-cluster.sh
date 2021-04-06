#!/bin/bash
set -o errexit

reg_name='local-registry'
cluster_name=${1:-local-k8s-cluster}

echo -n "Enter yes or no to delete cluster '$cluster_name' : "
read -r do_delete_cluster
do_delete_cluster="$(echo "${do_delete_cluster}" | tr '[:upper:]' '[:lower:]')"
if [ "$do_delete_cluster" == yes ]; then
    echo "Deleting cluster '$cluster_name'..."
    kind delete cluster --name "$cluster_name"
    echo "... Cluster '$cluster_name' deleted!"

    # Local Docker Registry deletion
    echo -n "Enter yes or no to delete local Docker registry '$reg_name' : "
    read -r do_delete_registry
    do_delete_registry="$(echo "${do_delete_registry}" | tr '[:upper:]' '[:lower:]')"
    if [ "$do_delete_registry" == yes ]; then
      docker container rm -f $reg_name || \
        echo "Failed to remove local registry '$reg_name'. You may want to attempt doing so yourself with the following command: docker container rm -f $reg_name"
    else
      echo "Local docker registry deletion aborted."
    fi
else
    echo "Cluster deletion aborted."
fi
