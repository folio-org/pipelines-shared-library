# Backups Module

This Terraform module sets up a Data Lifecycle Manager (DLM) policy for creating EBS snapshots for a Jenkins server on AWS. It includes configuration for the lifecycle policy, schedules, and tags.

## Usage

```hcl
module "backups" {
  source = "./modules/backups"

  name          = "jenkins-poc"
  dlm_role_arn  = "arn:aws:iam::123456789012:role/dlm-role"
  tags = {
    Environment = "dev"
    Project     = "jenkins"
  }
}
```

## Inputs

| Name           | Description                                                                 | Type     | Default | Required |
|----------------|-----------------------------------------------------------------------------|----------|---------|----------|
| `name`         | Name prefix for resources                                                   | `string` | n/a     | yes      |
| `dlm_role_arn` | ARN of the IAM role for DLM policy execution                                | `string` | `""`    | no       |
| `tags`         | Tags to apply to resources                                                  | `map(string)` | `{}` | no       |

## Outputs

None

## Resources

- `aws_dlm_lifecycle_policy.ebs_snapshot_policy`
