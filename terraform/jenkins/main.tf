locals {
  availability_zones = ["${var.region}a", "${var.region}b"]
}

module "network" {
  source             = "./modules/network"
  prefix             = var.prefix
  vpc_cidr           = var.vpc_cidr
  availability_zones = local.availability_zones
  aws_region         = var.region
  tags               = var.tags
}

module "jenkins_ec2" {
  depends_on = [
    module.network.nat_gateway_id,
    module.network.private_route_table_association_ids
  ]
  source              = "./modules/jenkins"
  prefix              = var.prefix
  vpc_id              = module.network.vpc_id
  subnet_id           = module.network.private_subnet_ids[0]
  ami_id              = var.ami_id
  instance_type       = var.instance_type
  volume_size         = var.volume_size
  volume_type         = var.volume_type
  enable_restore      = var.enable_restore
  restore_snapshot_id = var.restore_snapshot_id
  jenkins_version     = var.jenkins_version
  backup_bucket       = var.backup_bucket
  availability_zone   = local.availability_zones[0]
  tags                = var.tags
  ssh_key_name        = var.ssh_key_name
}

module "alb" {
  source              = "./modules/alb"
  prefix              = var.prefix
  vpc_id              = module.network.vpc_id
  subnet_ids          = module.network.public_subnet_ids
  jenkins_instance_id = module.jenkins_ec2.instance_id
  jenkins_sg_id       = module.jenkins_ec2.security_group_id
  tags                = var.tags
  certificate_arn     = var.certificate_arn
}

module "route53" {
  source       = "./modules/route53"
  zone_id      = var.route53_zone_id
  record_name  = var.route53_record_name
  alb_dns_name = module.alb.alb_dns_name
}

module "backups" {
  source = "./modules/backups"
  prefix = var.prefix
  tags   = var.tags
}

module "bastion" {
  source        = "./modules/bastion"
  prefix        = var.prefix
  vpc_id        = module.network.vpc_id
  subnet_id     = module.network.public_subnet_ids[0]
  ami_id        = var.ami_id
  instance_type = var.bastion_instance_type
  allowed_cidrs = var.allowed_cidrs
  key_pair_name = var.ssh_key_name
  jenkins_sg_id = module.jenkins_ec2.security_group_id
  tags          = var.tags
}