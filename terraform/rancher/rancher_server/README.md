```shell
$ terraform init
$ terraform state pull
```

```shell
$ terraform plan -out tfplan
$ terraform apply -input=false tfplan
```

```shell
$ terraform destroy \
  -auto-approve
```
