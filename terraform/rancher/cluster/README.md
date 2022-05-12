```bash
$ terraform init \
  -reconfigure \
  -backend-config=backends/config.folio-testing.backend
```


terraform plan -input=false -out tfplan

terraform apply -input=false tfplan
