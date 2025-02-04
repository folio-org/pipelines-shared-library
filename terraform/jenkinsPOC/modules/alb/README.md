# ALB Module

This Terraform module sets up an Application Load Balancer (ALB) for a Jenkins server on AWS. It includes configuration for listeners, target groups, and security group rules.

## Usage

```hcl
module "alb" {
  source = "./modules/alb"

  name                = "jenkins-poc"
  vpc_id              = "vpc-12345678"
  subnet_id           = "subnet-12345678"
  certificate_arn     = "arn:aws:acm:us-west-2:123456789012:certificate/abcd1234-5678-90ab-cdef-1234567890ab"
  jenkins_instance_id = "i-1234567890abcdef0"
  tags = {
    Environment = "dev"
    Project     = "jenkins"
  }
}
```

## Inputs

| Name                | Description                                                                 | Type     | Default | Required |
|---------------------|-----------------------------------------------------------------------------|----------|---------|----------|
| `name`              | Name prefix for resources                                                   | `string` | n/a     | yes      |
| `vpc_id`            | VPC ID where the ALB will be deployed                                       | `string` | n/a     | yes      |
| `subnet_id`         | Subnet ID where the ALB will be deployed                                    | `string` | n/a     | yes      |
| `certificate_arn`   | ARN of the SSL certificate for HTTPS listener                               | `string` | n/a     | yes      |
| `jenkins_instance_id` | ID of the Jenkins EC2 instance to attach to the target group              | `string` | n/a     | yes      |
| `tags`              | Tags to apply to resources                                                  | `map(string)` | `{}` | no       |

## Outputs

| Name                | Description                           |
|---------------------|---------------------------------------|
| `alb_dns_name`      | DNS name of the ALB                   |

## Resources

- `aws_lb.this`
- `aws_lb_target_group.this`
- `aws_lb_target_group_attachment.this`
- `aws_lb_listener.http`
- `aws_lb_listener.https`
- `aws_security_group_rule.alb_to_jenkins`
