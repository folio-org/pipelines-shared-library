Below is an example of a concise Terraform README file for the module. It describes the purpose, lists the dependencies,
provides usage examples, and details the input/output variables.

# Terraform EFS Cache Module

This module creates an AWS Elastic File System (EFS) with mount targets, an access point, and configures Kubernetes
persistent volumes (PV) and persistent volume claims (PVC) to use the EFS. It is designed to serve as a cache for
resources such as Maven or Yarn in Kubernetes environments.

## Features

- Creates an EFS file system with configurable encryption and tags.
- Optionally creates a security group for the EFS allowing NFS access.
- Provisions EFS mount targets for a list of subnets.
- Sets up an EFS access point with POSIX permissions.
- Creates a Kubernetes persistent volume and a persistent volume claim.

## Prerequisites

- Terraform \>= 1.6.1
- AWS provider (\~\> 5.0)
- Kubernetes provider (\~\> 2.36)
- An existing VPC and subnets in AWS
- Kubernetes cluster to use the PV/PVC

## Dependencies

- AWS services: EFS, Security Groups, and VPC networking.
- Kubernetes cluster accessible by the configured provider.

## Usage Example

```hcl
module "efs_cache" {
  source                 = "./terraform/jenkins/agents_efs/modules/efs_cache"
  name                   = "efs-cache"
  creation_token         = "unique-token"
  vpc_id                 = "vpc-xxxxxxxxxxxx"
  subnet_ids             = ["subnet-xxxxxxxxxxxx", "subnet-yyyyyyyyyyyy"]
  allowed_cidr_blocks    = ["10.0.0.0/16"]
  access_point_root      = "/cache"
  encrypted              = true
  tags                   = { Environment = "production" }

  # Kubernetes PV/PVC parameters
  k8s_pv_name            = "efs-cache-pv"
  k8s_pvc_name           = "efs-cache-pvc"
  k8s_namespace          = "default"
  pv_capacity            = "5Gi"
  pv_reclaim_policy      = "Retain"
  k8s_storage_class      = "efs-storage"
}
```

## Input Variables

| Name                          | Description                                                                     | Type           | Default             | Required |
|-------------------------------|---------------------------------------------------------------------------------|----------------|---------------------|----------|
| `name`                        | Name tag for the EFS file system                                                | `string`       | n/a                 | yes      |
| `creation_token`              | Creation token for the EFS file system                                          | `string`       | n/a                 | yes      |
| `encrypted`                   | Whether the file system should be encrypted                                     | `bool`         | `true`              | no       |
| `vpc_id`                      | The VPC ID where the EFS will be provisioned                                    | `string`       | n/a                 | yes      |
| `subnet_ids`                  | List of subnet IDs to create EFS mount targets in                               | `list(string)` | n/a                 | yes      |
| `allowed_cidr_blocks`         | CIDR blocks allowed to access EFS                                               | `list(string)` | `[\"10.0.0.0/16\"]` | no       |
| `posix_uid`                   | POSIX user ID for the access point                                              | `number`       | `1000`              | no       |
| `posix_gid`                   | POSIX group ID for the access point                                             | `number`       | `1000`              | no       |
| `access_point_root`           | Root directory for the access point (e.g., \`/yarn-cache\` or \`/maven-cache\`) | `string`       | n/a                 | yes      |
| `access_point_permissions`    | Permissions for the access point (e.g., \`755\`)                                | `string`       | \`755\`             | no       |
| `create_security_group`       | Whether to create a security group for this EFS                                 | `bool`         | `true`              | no       |
| `security_group_id`           | Optional existing security group ID to use for EFS                              | `string`       | `""`                | no       |
| `tags`                        | Additional tags for the created AWS resources                                   | `map(any)`     | `{}`                | no       |
| _Kubernetes PV/PVC Variables_ |                                                                                 |                |                     |          |
| `k8s_pv_name`                 | Name of the Kubernetes Persistent Volume                                        | `string`       | `""`                | no       |
| `k8s_pvc_name`                | Name of the Kubernetes Persistent Volume Claim                                  | `string`       | `""`                | no       |
| `k8s_namespace`               | Namespace for the PersistentVolumeClaim                                         | `string`       | `default`           | no       |
| `pv_capacity`                 | Capacity of the persistent volume (e.g., \`5Gi\`)                               | `string`       | `5Gi`               | no       |
| `pv_reclaim_policy`           | Persistent volume reclaim policy (\`Retain\`, \`Delete\`, \`Recycle\`)          | `string`       | `Retain`            | no       |
| `k8s_storage_class`           | Kubernetes Storage Class name to be used for the PV/PVC                         | `string`       | `""`                | no       |

## Outputs

| Name              | Description                                            |
|-------------------|--------------------------------------------------------|
| `efs_id`          | The ID of the created EFS file system                  |
| `access_point_id` | The ID of the created EFS access point                 |
| `k8s_pv_name`     | Name of the created Kubernetes Persistent Volume       |
| `k8s_pvc_name`    | Name of the created Kubernetes Persistent Volume Claim |
