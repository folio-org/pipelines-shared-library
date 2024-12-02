## Rancher network
This terraform scripts aimed to provision Jenkins with it's own AWS VPC

### Install 

```shell
terraform init
terraform state pull
terraform plan -input=false -out tfplan
terraform apply -input=false tfplan
```

### Destroy
```shell
terraform destroy -auto-approve
```

### Restore
**_Important:_**
terraform destroy needs to be done before restore
```
terraform destroy 
terraform apply -var "snapshot_id=<snap-ID>"
```

> **_NOTE:_** 
> Before commit changes in scripts do not forget to format
> ```shell
> terraform fmt
> ```
