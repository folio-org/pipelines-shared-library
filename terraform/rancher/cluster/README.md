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
