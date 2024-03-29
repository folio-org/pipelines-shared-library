```shell
$ terraform init
$ terraform workspace list
$ terraform workspace select <cluster-name> or terraform workspace new <cluster-name>
$ terraform state pull
```

```shell
$ terraform plan \
  -input=false \
  -out tfplan
$ terraform apply -input=false tfplan
```

```shell
$ terraform destroy \
  -auto-approve
```

[//]: # (TODO: update README with variables description )

# Installing EFK with Terraform on EKS

## Getting Started & Documentation

Documentation is available on the [Rancher website](https://rancher.com/docs/rancher/v2.5/en/logging/).

## 1. Enable logging

[Enabling logging](https://rancher.com/docs/rancher/v2.5/en/logging/#:~:text=Troubleshooting-,Enabling%20Logging,-You%20can%20enable) is implemented though rancher_app_logging.tf by installing rancher logging application solution.

Logging application for Rancher basically installs [Banzai Cloud Logging Operator](https://rancher.com/docs/rancher/v2.5/en/logging/architecture/#how-the-banzai-cloud-logging-operator-works). It deploys and configures a Fluent Bit DaemonSet on every node to collect container and application logs from the node file system.

Fluent Bit queries the Kubernetes API and enriches the logs with metadata about the pods, and transfers both the logs and the metadata to Fluentd. Fluentd receives, filters, and transfers logs to multiple Outputs.

The following custom resources are used to define how logs are filtered and sent to their Outputs:

- **A Flow** is a namespaced custom resource that uses filters and selectors to route log messages to the appropriate Outputs.
- **A ClusterFlow** is used to route cluster-level log messages.
  An Output is a namespaced resource that defines where the log messages are sent.
- **A ClusterOutput** defines an Output that is available from all Flows and ClusterFlows.
  Each **Flow** must reference an **Output**, and each **ClusterFlow** must reference a **ClusterOutput**.

The following figure from the Banzai documentation shows the new logging architecture:

![alt](https://rancher.com/docs/img/rancher/banzai-cloud-logging-operator.png)

## 2. Deploy Elasticsearch and Kibana

In the main.tf we deploy to the cluster [ECK operator](https://www.elastic.co/guide/en/cloud-on-k8s/current/k8s-install-helm.html) using helm, Kibana and Elasticsearch resources.

Additionally [Logging](https://github.com/banzaicloud/logging-operator/tree/master/config/crd/bases/logging.banzaicloud.io_loggings.yaml), [Flow](https://github.com/banzaicloud/logging-operator/tree/master/config/crd/bases/logging.banzaicloud.io_flows.yaml) and [Output](https://github.com/banzaicloud/logging-operator/tree/master/config/crd/bases/logging.banzaicloud.io_outputs.yaml) CRDs should be created to configured.

![alt](https://github.com/DariaPavlova1/rancher_efk/blob/main/Images/cluster.drawio.png)

helm upgrade --install okapi \
  --namespace test \
  --set image.repository=folioci/okapi \
  --set image.tag=5.0.0-SNAPSHOT.805 \
  --set resources.requests.memory=1024Mi \
  --set resources.limits.memory=2048Mi \
  --set service.type=NodePort \
  --set postJob.enabled=false \
  --set javaOptions="-XX:MaxRAMPercentage=85.0 -XX:MetaspaceSize=384m -XX:MaxMetaspaceSize=512m -Djava.awt.headless=true -Dstorage=postgres -Dpostgres_host=\$(OKAPI_HOST) -Dpostgres_port=5432 -Dpostgres_username=\$(OKAPI_DB_USER) -Dpostgres_password=\$(OKAPI_DB_PASSWORD) -Dpostgres_database=\$(OKAPI_DB) -Dlog4j.configurationFile=/etc/log4j2.xml -Dhost=okapi -Dokapiurl=http://okapi:9130 -Dloglevel=INFO -Ddeploy.waitIterations=90 --add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED" \
  --set replicaCount=1 \
  folio-helm/okapi
