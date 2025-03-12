# Terraform Jenkins Agents EFS

This project provisions AWS resources for Jenkins agents. It creates an EFS and its associated mount targets, access
point and integrates with Kubernetes by provisioning Persistent Volumes (PV) and Persistent Volume Claims (PVC) for both
Yarn and Maven caches.

## Description

The project retrieves an existing VPC based on a tag and finds the private subnets for the availability zones. It then
deploys two EFS cache modules:

- A Yarn cache EFS.
- A Maven cache EFS.

Both modules create an EFS file system in the given VPC along with the required mount targets. They also provision a
Kubernetes PV and PVC for use with Jenkins agents.

## Variables

| Name                   | Description                                      | Type          | Default                             | Required |
|------------------------|--------------------------------------------------|---------------|-------------------------------------|----------|
| aws\_region            | AWS region to deploy resources                   | string        | us-west-2                           | no       |
| eks\_cluster\_name     | Name of the EKS cluster                          | string        | folio-jenkins-agents                | no       |
| vpc\_name              | VPC name to deploy EFS in                        | string        | folio-rancher-vpc                   | no       |
| allowed\_cidr\_blocks  | CIDR blocks allowed to access EFS                | list(string)  | [\"10.0.0.0/16\", \"192.168.0.0/16\"] | no       |
| tags                   | Default tags to apply to all resources           | map(any)      | {Terraform: \"true\", Project: \"folio\", Team: \"kitfox\"} | no       |

The module-level variables for EFS configuration are defined in the referenced modules (for yarn and maven caches) and
include parameters such as `name`, `creation_token`, `subnet_ids`, `access_point_root`, `posix_uid`, `posix_gid`, and
Kubernetes specific variables.

## Outputs

| Output Name                   | Description                              |
|-------------------------------|------------------------------------------|
| yarn\_cache\_efs\_id          | EFS ID for Yarn Cache                    |
| yarn\_cache\_access\_point\_id| Access Point ID for Yarn Cache           |
| yarn\_cache\_k8s\_pv\_name     | Kubernetes PV name for Yarn Cache        |
| yarn\_cache\_k8s\_pvc\_name    | Kubernetes PVC name for Yarn Cache       |
| maven\_cache\_efs\_id         | EFS ID for Maven Cache                   |
| maven\_cache\_access\_point\_id| Access Point ID for Maven Cache          |
| maven\_cache\_k8s\_pv\_name    | Kubernetes PV name for Maven Cache       |
| maven\_cache\_k8s\_pvc\_name   | Kubernetes PVC name for Maven Cache      |

## Usage Example

Below is an example of how the modules are called in the project:

```terraform
data "aws_vpc" "this" {
  filter {
    name   = "tag:Name"
    values = [var.vpc_name]
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "availability-zone"
    values = ["${var.aws_region}a", "${var.aws_region}b"]
  }
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.this.id]
  }
  tags = {
    Type : "private"
  }
}

module "yarn_cache" {
  source                = "./modules/efs_cache"
  name                  = "folio-jenkins-yarn-cache-efs"
  creation_token        = "folio-jenkins-yarn-cache"
  vpc_id                = data.aws_vpc.this.id
  subnet_ids            = data.aws_subnets.private.ids
  allowed_cidr_blocks   = var.allowed_cidr_blocks
  access_point_root     = "/yarn-cache"
  posix_uid             = 1000
  posix_gid             = 1000
  create_security_group = true

  # Kubernetes PV/PVC parameters
  k8s_pv_name       = "yarn-cache-pv"
  k8s_pvc_name      = "yarn-cache-pvc"
  pv_capacity       = "10Gi"
  pv_reclaim_policy = "Retain"
  k8s_storage_class = ""
  k8s_namespace     = "jenkins-agents"
}

module "maven_cache" {
  source                = "./modules/efs_cache"
  name                  = "folio-jenkins-maven-cache-efs"
  creation_token        = "folio-jenkins-maven-cache"
  vpc_id                = data.aws_vpc.this.id
  subnet_ids            = data.aws_subnets.private.ids
  allowed_cidr_blocks   = var.allowed_cidr_blocks
  access_point_root     = "/maven-cache"
  posix_uid             = 1000
  posix_gid             = 1000
  create_security_group = true

  # Kubernetes PV/PVC parameters
  k8s_pv_name       = "maven-cache-pv"
  k8s_pvc_name      = "maven-cache-pvc"
  pv_capacity       = "10Gi"
  pv_reclaim_policy = "Retain"
  k8s_storage_class = ""
  k8s_namespace     = "jenkins-agents"
}
```

## Execution Manual

1. **Initialize Terraform**  
   Run the following command to initialize the Terraform project, which installs the required providers and modules:
   ```bash
   terraform init
   ```

2. **Review the Execution Plan**  
   View the planned execution to check what resources will be created:
   ```bash
   terraform plan
   ```

3. **Apply the Changes**  
   Deploy the configuration:
   ```bash
   terraform apply
   ```

4. **Verify Outputs**  
   After deployment, view the outputs to review resource identifiers:
   ```bash
   terraform output
   ```
