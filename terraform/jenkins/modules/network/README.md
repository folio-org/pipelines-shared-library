# Network Terraform Module

This Terraform module provisions a **VPC network infrastructure** for Jenkins, including **public and private subnets,
an Internet Gateway, NAT Gateway, and VPC Peering**.

## Features

- **Creates a VPC** with DNS support
- **Public and Private Subnets** with route tables
- **Internet Gateway** for public subnet internet access
- **NAT Gateway** to allow private subnet outbound internet access
- **VPC Peering** to connect the Jenkins VPC with an existing Rancher VPC
- **Configurable CIDR blocks and availability zones**

## Prerequisites

- Terraform >= 1.6.1
- AWS provider configured with the necessary permissions

## Usage

```hcl
module "network" {
  source             = "./path-to-module"
  prefix             = "folio"
  vpc_cidr           = "10.0.0.0/16"
  availability_zones = ["us-west-2a", "us-west-2b"]
  aws_region         = "us-west-2"
  tags               = { Team = "Kitfox" }
}
```

## Inputs

| Name                 | Description                             | Type         | Default | Required |
|----------------------|-----------------------------------------|--------------|---------|----------|
| `prefix`             | Prefix for naming AWS resources         | `string`     | n/a     | yes      |
| `vpc_cidr`           | CIDR block for the VPC                  | `string`     | n/a     | yes      |
| `availability_zones` | List of at least two Availability Zones | list(string) | n/a     | yes      |
| `aws_region`         | AWS region for resource deployment      | `string`     | n/a     | yes      |
| `tags`               | Additional tags to apply to resources   | `map(any)`   | `{}`    | no       |

## Outputs

| Name                        | Description                                   |
|-----------------------------|-----------------------------------------------|
| `vpc_id`                    | ID of the created VPC                         |
| `public_subnet_ids`         | List of public subnet IDs                     |
| `private_subnet_ids`        | List of private subnet IDs                    |
| `vpc_peering_connection_id` | ID of the VPC peering connection with Rancher |
