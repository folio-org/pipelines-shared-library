# Route53 Module

This Terraform module sets up Route53 DNS records for a Jenkins server on AWS. It includes configuration for creating an alias record pointing to an Application Load Balancer (ALB).

## Usage

```hcl
module "route53" {
  source = "./modules/route53"

  zone_id     = "Z3P5QSUBK4POTI"
  record_name = "jenkins.example.com"
  alb_dns_name = module.alb.alb_dns_name
}
```

## Inputs

| Name          | Description                       | Type     | Default | Required |
|---------------|-----------------------------------|----------|---------|----------|
| `zone_id`     | Route53 Hosted Zone ID            | `string` | n/a     | yes      |
| `record_name` | DNS record name (e.g. jenkins.example.com) | `string` | n/a     | yes      |
| `alb_dns_name`| DNS name of the ALB               | `string` | n/a     | yes      |

## Outputs

| Name   | Description                                |
|--------|--------------------------------------------|
| `fqdn` | The fully qualified domain name for Jenkins.|

## Resources

- `aws_route53_record.this`
- `aws_lb_hosted_zone_id.this`
