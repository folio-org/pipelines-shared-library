# Route53 Terraform Module

This Terraform module creates a **Route53 DNS record** for a Jenkins instance, mapping it to an **AWS Application Load Balancer (ALB)**.

## Features

- Creates an **A record** in an existing Route53 Hosted Zone
- Uses an **alias record** to map the domain to an ALB DNS name
- Supports **health evaluation** to route traffic only when ALB is healthy

## Prerequisites

- Terraform >= 1.6.1
- AWS provider configured with proper permissions
- A **Route53 Hosted Zone** must already exist

## Usage

```hcl
module "route53" {
  source        = "./path-to-module"
  zone_id       = "ZXXXXXXXXXXXXXX"  # Replace with your Hosted Zone ID
  record_name   = "jenkins.example.com"
  alb_dns_name  = "my-alb-1234567890.us-east-1.elb.amazonaws.com"
}
```

## Inputs

| Name          | Description                                    | Type     | Default | Required |
|--------------|------------------------------------------------|---------|---------|----------|
| `zone_id`    | Route53 Hosted Zone ID                        | `string` | n/a     | yes      |
| `record_name`| Fully qualified domain name (e.g., jenkins.example.com) | `string` | n/a | yes |
| `alb_dns_name` | DNS name of the ALB                        | `string` | n/a     | yes      |

## Outputs

| Name    | Description                                  |
|---------|----------------------------------------------|
| `fqdn`  | The fully qualified domain name (FQDN) of the created Route53 record |
