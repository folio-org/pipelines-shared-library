# Jenkins ALB Terraform Module

This Terraform module provisions an Application Load Balancer (ALB) for a Jenkins instance on AWS.

## Features

- Creates an ALB with security groups
- Configures HTTP and HTTPS listeners
- Redirects HTTP traffic to HTTPS
- Defines a target group for Jenkins on port 8080
- Attaches a Jenkins instance to the target group
- Configures security group rules

## Prerequisites

- Terraform >= 1.6.1
- AWS provider configured

## Usage

```hcl
module "alb" {
  source              = "./path-to-module"
  prefix              = "folio"
  vpc_id              = "vpc-xxxxxxxx"
  subnet_ids          = ["subnet-xxxxxxxx", "subnet-xxxxxxxx"]
  certificate_arn     = "arn:aws:acm:region:account-id:certificate/certificate-id"
  jenkins_instance_id = "i-xxxxxxxxxxxx"
  jenkins_sg_id       = "sg-xxxxxxxxxxxx"
  tags                = { Team = "Kitfox" }
}
```

## Inputs

| Name                  | Description                                      | Type   | Default | Required |
|-----------------------|--------------------------------------------------|--------|---------|----------|
| `prefix`              | Prefix for naming AWS resources                  | string | n/a     | yes      |
| `vpc_id`              | VPC ID where the ALB will be created             | string | n/a     | yes      |
| `subnet_ids`          | List of subnet IDs for ALB deployment            | list(string) | n/a  | yes      |
| `certificate_arn`     | ARN of the ACM SSL certificate for HTTPS         | string | n/a     | yes      |
| `jenkins_instance_id` | ID of the Jenkins instance to register       | string | n/a     | yes      |
| `jenkins_sg_id`       | Security Group ID for the Jenkins instance       | string | n/a     | yes      |
| `tags`                | A map of tags to apply to resources              | map(string) | `{}` | no       |

## Outputs

| Name          | Description                                  |
|--------------|----------------------------------------------|
| `alb_dns`    | DNS name of the ALB                         |
