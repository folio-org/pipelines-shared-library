## Folio OpensSearch
This terraform scripts aimed to provision shared AWS OpenSearch service for rancher clusters

### Install 

```shell
terraform init
terraform state pull
terraform plan -input=false -out tfplan
terraform apply -input=false tfplan
```

### Destroy
```shell
terraform init
terraform destroy -auto-approve
```



> **_NOTE:_** 
> Before commit changes in scripts do not forget to format
> ```shell
> terraform fmt
> ```
