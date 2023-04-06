locals {
  cidr_block     = var.vpc_cidr_block
  start_position = 0
  offset         = length(data.aws_availability_zones.this.zone_ids)
  public_subnets = [
    for zone_id in data.aws_availability_zones.this.zone_ids :
    cidrsubnet(local.cidr_block, var.subnet_prefix_extension, tonumber(substr(zone_id, length(zone_id) - 1, 1)) + local.start_position - 1)
  ]
  private_subnets = [
    for zone_id in data.aws_availability_zones.this.zone_ids :
    cidrsubnet(local.cidr_block, var.subnet_prefix_extension, tonumber(substr(zone_id, length(zone_id) - 1, 1)) + local.start_position + local.offset - 1)
  ]
  database_subnets = [
    for zone_id in data.aws_availability_zones.this.zone_ids :
    cidrsubnet(local.cidr_block, var.subnet_prefix_extension, tonumber(substr(zone_id, length(zone_id) - 1, 1)) + local.start_position + local.offset * 2 - 1)
  ]
  clusters_tags = { for cluster in var.clusters : "kubernetes.io/cluster/${cluster}" => "shared" }
}
