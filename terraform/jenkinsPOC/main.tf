module "jenkins_ec2" {
  source              = "./modules/jenkins"
  name                = var.name
  vpc_id              = var.vpc_id
  subnet_id           = var.subnet_id
  ami_id              = var.ami_id
  instance_type       = var.instance_type
  volume_size         = var.volume_size
  volume_type         = var.volume_type
  enable_restore      = var.enable_restore
  restore_snapshot_id = var.restore_snapshot_id
  jenkins_version     = var.jenkins_version
  jenkins_plugins     = var.jenkins_plugins
  backup_bucket       = var.backup_bucket
  availability_zone   = var.availability_zone
  tags                = var.tags
}

module "alb" {
  source              = "./modules/alb"
  name                = var.name
  vpc_id              = var.vpc_id
  subnet_id           = var.subnet_id
  jenkins_instance_id = module.jenkins_ec2.instance_id
  jenkins_sg_id       = module.jenkins_ec2.security_group_id
  tags                = var.tags
}

module "route53" {
  source       = "./modules/route53"
  zone_id      = var.route53_zone_id
  record_name  = var.route53_record_name
  alb_dns_name = module.alb.alb_dns_name
}

module "backups" {
  source = "./modules/backups"
  name   = var.name
  tags   = var.tags
}