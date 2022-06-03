```
$ terraform init \
  -reconfigure \
  -backend-config=backends/config.karate.backend
$ terraform state pull
```

```
$ terraform plan \
  -input=false \
  -out tfplan \
  -var rancher_cluster_name=folio-testing \
  -var rancher_project_name=karate
$ terraform apply -input=false tfplan
```

```
$ terraform destroy \
  -auto-approve \
  -var rancher_cluster_name=folio-testing \
  -var rancher_project_name=karate
```

### Required parameters

**aws_access_key** `Default: ""`

**aws_secret_key** `Default: ""`

**rancher_cluster_name** `Default: ""`

**rancher_project_name** `Default: ""`

**rancher_token_key** `Default: ""`

**folio_docker_registry_username** `Default: ""`

**folio_docker_registry_password** `Default: ""`

**stripes_image_tag** `Default: ""`

**pg_password** `Default: ""`

**pgadmin_password** `Default: ""`

### Optional parameters

**folio_repository** `Default: "core"`

**folio_release** `Default: "master"`

### Examples
Example of content for secrets.auto.tfvars
```
#AWS credentials
aws_access_key = "<ACCESS-KEY>"
aws_secret_key = "<SECRET-KEY>"

#Rancher related properties
rancher_token_key    = "<TOKEN>"

#Modules related properties
folio_repository               = "core"
folio_release                  = "master"
folio_docker_registry_username = "<USERNAME>"
folio_docker_registry_password = "<PASSWORD>"
pg_password                    = "<PASSWORD>"
pgadmin_password               = "<PASSWORD>"
stripes_image_tag              = "<TAG>"

```


terraform plan '-input=false' -out tfplan -var 'okapi_version=latest' -var 'tenant_id=diku' -var 'cluster_name=folio-testing' -var 'project_name=sprint' -var 'folio_repository=complete' -var 'env_type=development' -var 'folio_release=snapshot' -var 'stripes_image_tag=folio-testing-sprint-diku-b17158e' -var 'pg_password=postgres_password_123!' -var 'pgadmin_password=SuperSecret' -var 'github_team_ids=[]' -var 'folio_docker_registry_username=folio-docker-dev' -var 'folio_docker_registry_password=FolioDockerDev5757'
