# Bastion Host Terraform Module

This Terraform module provisions a **Bastion Host** in AWS to securely access Jenkins instances.

## Features

- Deploys an **EC2 instance** as a Bastion Host
- Creates a **Security Group** for controlled SSH access
- Allows **SSH access only from specified CIDRs**
- Enables **Bastion-to-Jenkins SSH access**

## Prerequisites

- Terraform >= 1.6.1
- AWS provider configured with proper permissions

## Usage

```hcl
module "bastion" {
  source          = "./path-to-module"
  prefix          = "folio"
  ami_id          = "ami-xxxxxxxxxxxx"  # Replace with a valid AMI ID
  instance_type   = "t3.micro"
  subnet_id       = "subnet-xxxxxxxxxxxx"
  key_pair_name   = "my-key-pair"
  vpc_id          = "vpc-xxxxxxxxxxxx"
  allowed_cidrs   = ["192.168.1.0/24"]  # Restrict SSH access
  jenkins_sg_id   = "sg-xxxxxxxxxxxx"   # Jenkins security group ID
  tags            = { Team = "Kitfox" }
}
```

## Inputs

| Name            | Description                                      | Type         | Default | Required |
|----------------|--------------------------------------------------|-------------|---------|----------|
| `prefix`       | Prefix for naming AWS resources                  | `string`    | n/a     | yes      |
| `ami_id`       | AMI ID for the Bastion Host                      | `string`    | n/a     | yes      |
| `instance_type` | Instance type for the Bastion Host               | `string`    | n/a | yes |
| `subnet_id`    | Public subnet ID where the Bastion is deployed   | `string`    | n/a     | yes      |
| `key_pair_name`| Key pair name for SSH access                      | `string`    | n/a     | yes      |
| `vpc_id`       | VPC ID where the Bastion is created               | `string`    | n/a     | yes      |
| `allowed_cidrs`| List of CIDR blocks allowed for SSH access       | `list(string)` | n/a | yes |
| `jenkins_sg_id`| Security Group ID of the Jenkins instance        | `string`    | n/a     | yes      |
| `tags`         | A map of tags to apply to resources               | `map(string)` | `{}`  | no       |

## Outputs

| Name                  | Description                           |
|----------------------|---------------------------------------|
| `bastion_instance_id` | ID of the deployed Bastion instance   |
| `bastion_private_ip`   | Private IP of the Bastion Host        |
| `bastion_public_ip`   | Public IP of the Bastion Host         |
| `bastion_sg_id`       | Security Group ID of the Bastion Host |
