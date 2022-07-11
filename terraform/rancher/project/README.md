```shell
$ terraform init
$ terraform workspace list
$ terraform workspace select <cluster-name>-<project-name> or terraform workspace new <cluster-name>-<project-name>
$ terraform state pull
```

```shell
$ terraform plan \
  -input=false \
  -out tfplan \
  -var frontend_image_tag=<image> \
  -var okapi_version=<version> \
  -var rancher_cluster_name=<cluster-name> \
  -var rancher_project_name=<project-name>
$ terraform apply -input=false tfplan
```

```shell
$ terraform destroy \
  -auto-approve \
  -var frontend_image_tag=<image> \
  -var okapi_version=<version> \
  -var rancher_cluster_name=<cluster-name> \
  -var rancher_project_name=<project-name>
```

### Required parameters

**aws_access_key** `Default: ""`

**aws_secret_key** `Default: ""`

**rancher_cluster_name** `Default: ""`

**rancher_project_name** `Default: ""`

**rancher_token_key** `Default: ""`

**folio_docker_registry_username** `Default: ""`

**folio_docker_registry_password** `Default: ""`

**frontend_image_tag** `Default: ""`

**pgadmin_password** `Default: ""`

### Optional parameters

**repository** `Default: "platform-complete"`

**branch** `Default: "snapshot"`

**pg_password** `Default: ""`

### Examples
Example of content for secrets.auto.tfvars
```
#AWS credentials
aws_access_key = "<ACCESS-KEY>"
aws_secret_key = "<SECRET-KEY>"

#Rancher related properties
rancher_token_key    = "<TOKEN>"

#Modules related properties
repository               = "platform-complete"
branch                  = "snapshot"
folio_docker_registry_username = "<USERNAME>"
folio_docker_registry_password = "<PASSWORD>"
pg_password                    = "<PASSWORD>"
pgadmin_password               = "<PASSWORD>"
frontend_image_tag              = "<TAG>"
```
