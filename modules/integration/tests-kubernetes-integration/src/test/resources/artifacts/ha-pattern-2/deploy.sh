#! /bin/bash

# Copyright (c) 2017, WSO2 Inc. (http://wso2.com) All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

prgdir=$(dirname "$0")
script_path=$(cd "$prgdir"; cd ..; pwd)
echo "Current location : "$script_path

sp_port_one=32016
sp_port_two=32017
docker_server=$(grep -r "docker_server" $script_path/docker-files/docker-registry.properties  | sed -e 's/docker_server=//' | tr -d '"')
docker_user=$(grep -r "docker_user" $script_path/docker-files/docker-registry.properties  | sed -e 's/docker_user=//' | tr -d '"')
PASSWORD=$(grep -r "docker_pw" $script_path/docker-files/docker-registry.properties  | sed -e 's/docker_pw=//' | tr -d '"')

# ----- K8s master url needs to be export from /k8s.properties

K8s_master=$(echo $(cat $script_path/../infrastructure-automation/k8s.properties))
export $K8s_master
echo "Kubernetes Master URL is Set to : "$K8s_master
echo "Creating the K8S Pods!!!!"

#----- ## This is to create K8s svc, rc, pods and containers

# -----# To create docker registry key
#This part should be remove from here and update as onetime task form somewhere: no need to run again and again
kubectl create secret docker-registry regsecretdas --docker-server=$docker_server --docker-username=$docker_user --docker-password=$docker_pw --docker-email=$docker_user@wso2.com
echo "registry key created"

echo "Creating the MySQL RC and Service!"
kubectl create -f $script_path/ha-scripts/mysql_service.yaml
kubectl create -f $script_path/ha-scripts/mysql_rc.yaml
sleep 10

echo "Creating the SP Instances!"
kubectl create -f $script_path/ha-scripts/sp-ha-node-1-service.yaml
kubectl create -f $script_path/ha-scripts/sp-ha-node-1-rc.yaml
sleep 2

kubectl create -f $script_path/ha-scripts/sp-ha-node-2-service.yaml
kubectl create -f $script_path/ha-scripts/sp-ha-node-2-rc.yaml
sleep 2

sp_port_one=$(kubectl get service sp-ha-node-1 -o=jsonpath="{$.spec.ports[?(@.name=='servlet-http')].nodePort}")
msf4j_port_one=$(kubectl get service sp-ha-node-1 -o=jsonpath="{$.spec.ports[?(@.name=='msf4j-http')].nodePort}")

sp_port_two=$(kubectl get service sp-ha-node-2 -o=jsonpath="{$.spec.ports[?(@.name=='servlet-http')].nodePort}")
msf4j_port_two=$(kubectl get service sp-ha-node-2 -o=jsonpath="{$.spec.ports[?(@.name=='msf4j-http')].nodePort}")

echo "sp_port" $sp_port_one
echo "sp_port" $sp_port_two


#----- ## To retrieve IP addresses of relevant nodes

function getKubeNodeIP() {
    provider=$(kubectl get node $1 -o=jsonpath="{$.spec.providerID}")
    if [[ $provider = *"aws"* ]]; then
      instance_id=$(kubectl get node $1 -o=jsonpath="{$.spec.externalID}")
      echo $(aws ec2 describe-instances --instance-id $instance_id --query 'Reservations[].Instances[].PublicIpAddress' --output=text)
    else
      node_ip=$(kubectl get node $1 -o template --template='{{range.status.addresses}}{{if eq .type "ExternalIP"}}{{.address}}{{end}}{{end}}')
      if [ -z $node_ip ]; then
        echo $(kubectl get node $1 -o template --template='{{range.status.addresses}}{{if eq .type "InternalIP"}}{{.address}}{{end}}{{end}}')
      else
        echo $node_ip
      fi
    fi
}

function getNodeIpByPod(){
    provider=$(kubectl get nodes -o=jsonpath="{$.items[0].spec.providerID}")
    if [[ $provider = *"aws"* ]]; then
      nodeName=$(kubectl get pod $1 -o=jsonpath="{$.spec.nodeName}")
      echo $(getKubeNodeIP $nodeName)
    else
      echo $(kubectl get pods $1 --output=jsonpath={.status.hostIP})
    fi
}



kube_nodes=($(kubectl get nodes | awk '{if (NR!=1) print $1}'))
host=$(getKubeNodeIP "${kube_nodes[0]}")
echo "Waiting for Pods to startup"
sleep 10

#----- ## To check the server start success

# ----- the loop is used as a global timer. Current loop timer is 3*100 Sec.
while true
do
echo $(date)" Checking for Node 1 status on http://${host}:${sp_port_one}"
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' --fail --connect-timeout 5 --header 'Authorization: Basic YWRtaW46YWRtaW4=' http://${host}:${sp_port_one}/siddhi-apps)
  #curl --silent --get --fail --connect-timeout 5 --max-time 10 http://192.168.58.21:32013/siddhi-apps  ## to get response body
  if [ $STATUS -eq 200 ]; then
    echo "Node 1 successfully started."
    break
  else
    echo "Got $STATUS. Node 1 not started properly. "
  fi
  sleep 10
done

host=$(getKubeNodeIP "${kube_nodes[0]}")
while true
do
echo $(date)" Checking for Node 2 status on http://${host}:${sp_port_two}"
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' --fail --connect-timeout 5 --header 'Authorization: Basic YWRtaW46YWRtaW4=' http://${host}:${sp_port_two}/siddhi-apps)
  #curl --silent --get --fail --connect-timeout 5 --max-time 10 http://192.168.58.21:32013/siddhi-apps  ## to get response body
  if [ $STATUS -eq 200 ]; then
    echo "Node 2 successfully started."
    break
  else
    echo "Got $STATUS. Node 2 not started properly. "
  fi
  sleep 10
done


trap : 0

echo >&2 '
************
*** DONE ***
************
'

echo 'Generating The test-deployment.json!'
pods=$(kubectl get pods --output=jsonpath={.items..metadata.name})
json='['
for pod in $pods; do
         hostip=$(getNodeIpByPod $pod)
         label=$(kubectl get pods "$pod" --output=jsonpath={.metadata.labels.name})
         servicedata=$(kubectl describe svc "$label")
         json+='{"hostIP" :"'$hostip'", "label" :"'$label'", "ports" :['
         declare -a dataarray=($servicedata)
         let count=0
         for data in ${dataarray[@]}  ; do
            if [ "$data" = "NodePort:" ]; then
            IFS='/' read -a myarray <<< "${dataarray[$count+2]}"
            json+='{'
            json+='"protocol" :"'${dataarray[$count+1]}'",  "port" :"'${myarray[0]}'"'
            json+="},"
            fi

         ((count+=1))
         done
         i=$((${#json}-1))
         lastChr=${json:$i:1}

         if [ "$lastChr" = "," ]; then
         json=${json:0:${#json}-1}
         fi

         json+="]},"
done

json=${json:0:${#json}-1}
json+="]"

echo $json;

cat > test-deployment.json << EOF1
$json
EOF1
