# Jenkins EBS Backup Terraform Module

This Terraform module configures an **AWS Data Lifecycle Manager (DLM) policy** to automate **EBS volume snapshots** for Jenkins.

## Features

- Automates snapshot creation for Jenkins EBS volumes
- Defines a **24-hour snapshot interval**
- Retains snapshots for **180 days**
- Uses AWS **Data Lifecycle Manager (DLM)**
- Supports **IAM role-based execution**

## Prerequisites

- Terraform >= 1.6.1
- AWS provider configured with appropriate IAM permissions

## Usage

```hcl
module "jenkins_backups" {
  source         = "./path-to-module"
  prefix         = "folio"
  tags           = { Team = "Kitfox" }
}
```

## Inputs

| Name          | Description                                      | Type         | Default | Required |
|--------------|--------------------------------------------------|-------------|--------|----------|
| `prefix`     | Prefix for backup resource naming               | `string`    | n/a    | yes      |
| `tags`       | A map of additional tags                        | `map(string)` | `{}`   | no       |
