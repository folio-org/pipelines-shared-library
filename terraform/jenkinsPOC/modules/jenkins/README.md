# Jenkins Module

This Terraform module sets up a Jenkins server on an AWS EC2 instance with an attached EBS volume for Jenkins data storage. It also configures a nightly backup of Jenkins data to an S3 bucket.

## Usage

```hcl
module "jenkins" {
  source = "./modules/jenkins"

  name                = "jenkins-poc"
  vpc_id              = "vpc-12345678"
  subnet_id           = "subnet-12345678"
  ami_id              = "ami-12345678"
  instance_type       = "t3.medium"
  availability_zone   = "us-west-2a"
  volume_size         = 50
  volume_type         = "gp2"
  enable_restore      = false
  restore_snapshot_id = null
  jenkins_version     = "2.289.1"
  jenkins_plugins     = ["git", "workflow-aggregator"]
  backup_bucket       = "my-jenkins-backups"
  tags = {
    Environment = "dev"
    Project     = "jenkins-poc"
  }
}
```

## Inputs

| Name                | Description                                                                 | Type     | Default | Required |
|---------------------|-----------------------------------------------------------------------------|----------|---------|----------|
| `name`              | Name prefix for resources                                                   | `string` | n/a     | yes      |
| `vpc_id`            | VPC ID where the Jenkins instance will be deployed                          | `string` | n/a     | yes      |
| `subnet_id`         | Subnet ID where the Jenkins instance will be deployed                       | `string` | n/a     | yes      |
| `ami_id`            | AMI ID for the Jenkins instance                                             | `string` | n/a     | yes      |
| `instance_type`     | EC2 instance type for Jenkins                                               | `string` | n/a     | yes      |
| `availability_zone` | Availability zone for the EBS volume                                        | `string` | n/a     | yes      |
| `volume_size`       | Size of the EBS volume in GB                                                | `number` | n/a     | yes      |
| `volume_type`       | Type of the EBS volume                                                      | `string` | n/a     | yes      |
| `enable_restore`    | Whether to restore Jenkins data from a snapshot                             | `bool`   | `false` | no       |
| `restore_snapshot_id` | Snapshot ID to restore Jenkins data from (if `enable_restore` is `true`)  | `string` | `null`  | no       |
| `jenkins_version`   | Version of Jenkins to install                                               | `string` | n/a     | yes      |
| `jenkins_plugins`   | List of Jenkins plugins to install                                          | `list(string)` | `[]` | no       |
| `backup_bucket`     | S3 bucket name for Jenkins data backups                                     | `string` | n/a     | yes      |
| `tags`              | Tags to apply to resources                                                  | `map(string)` | `{}` | no       |

## Outputs

| Name                | Description                           |
|---------------------|---------------------------------------|
| `instance_id`       | ID of the Jenkins EC2 instance        |
| `security_group_id` | ID of the Jenkins security group      |
| `ebs_volume_id`     | ID of the Jenkins EBS volume          |

## Resources

- `aws_security_group.jenkins_sg`
- `aws_instance.jenkins`
- `aws_ebs_volume.jenkins_data`
- `aws_volume_attachment.jenkins_data_attachment`

## Scripts

### `user_data.sh`

This script performs the following actions:
- Updates packages and installs necessary dependencies
- Sets up the Jenkins repository and installs Jenkins
- Formats and mounts the EBS volume for Jenkins data
- Enables and starts the Jenkins service
- Installs specified Jenkins plugins
- Sets up a nightly cron job to back up Jenkins data to an S3 bucket
