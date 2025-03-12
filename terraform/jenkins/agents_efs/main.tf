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