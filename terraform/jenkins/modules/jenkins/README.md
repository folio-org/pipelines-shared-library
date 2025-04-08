# Jenkins Terraform Module

This Terraform module deploys a **Jenkins EC2 instance** with **automated plugin installation, persistent storage, and S3 backups**.

## Features

- Deploys a **Jenkins EC2 instance** with configurable AMI, instance type, and security group
- Attaches an **EBS volume** for persistent Jenkins data storage
- Configures **S3 backups** for Jenkins data
- Enables **EBS snapshot restoration** for disaster recovery

## Prerequisites

- Terraform >= 1.6.1
- AWS provider configured with required permissions
- An **existing VPC and subnet** for the Jenkins instance

## Usage

```hcl
module "jenkins" {
  source             = "./path-to-module"
  prefix             = "folio"
  vpc_id            = "vpc-xxxxxxxxxxxx"
  subnet_id         = "subnet-xxxxxxxxxxxx"
  ami_id            = "ami-xxxxxxxxxxxx"
  instance_type     = "t3.medium"
  ssh_key_name      = "my-key-pair"
  volume_size       = 50
  availability_zone = "us-west-2a"
  enable_restore    = false
  restore_snapshot_id = ""
  jenkins_version   = "2.387.2"
  backup_bucket     = "my-jenkins-backups"
  tags              = { Team = "Kitfox" }
}
```

## Inputs

| Name               | Description                                      | Type         | Default | Required |
|-------------------|------------------------------------------------|-------------|---------|----------|
| `prefix`         | Prefix for naming AWS resources                  | `string`    | n/a     | yes      |
| `vpc_id`         | VPC ID where the Jenkins instance is deployed    | `string`    | n/a     | yes      |
| `subnet_id`      | Subnet ID for the Jenkins EC2 instance           | `string`    | n/a     | yes      |
| `ami_id`         | AMI ID for the Jenkins EC2 instance              | `string`    | n/a     | yes      |
| `instance_type`  | EC2 instance type for Jenkins                    | `string`    | n/a | no |
| `ssh_key_name`   | SSH key pair name for access                     | `string`    | n/a     | no      |
| `volume_size`    | Size of the attached EBS volume (GB)             | `number`    | n/a    | no       |
| `volume_type`    | Type of EBS volume                               | `string`    | `"gp3"` | no       |
| `availability_zone` | Availability Zone for the EBS volume          | `string`    | n/a     | no      |
| `enable_restore` | Whether to restore from an EBS snapshot         | `bool`      | `false` | no       |
| `restore_snapshot_id` | Snapshot ID for restoring EBS volume       | `string`    | `""`    | no       |
| `jenkins_version` | Jenkins version to install                     | `string`    | n/a | no |
| `backup_bucket`  | S3 bucket name for storing Jenkins backups       | `string`    | n/a     | no      |
| `tags`           | Additional tags for AWS resources               | `map(any)`  | `{}`    | no       |

## Outputs

| Name               | Description                                      |
|-------------------|------------------------------------------------|
| `instance_id`     | ID of the Jenkins EC2 instance                  |
| `security_group_id` | Security Group ID for the Jenkins instance   |
| `ebs_volume_id`   | ID of the attached EBS volume                   |
