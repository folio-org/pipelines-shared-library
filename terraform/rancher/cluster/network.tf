#Get all available AZs in our region
data "aws_availability_zones" "available_azs" {
  state = "available"
}

data "aws_vpc" "vpc" {
  count = var.vpc_create ? 0 : 1
  id    = var.vpc_id
}

data "aws_subnets" "private" {
  count = var.vpc_create ? 0 : 1
  filter {
    name   = "vpc-id"
    values = [var.vpc_id]
  }
  tags = {
    Type = "private"
  }
}

# reserve Elastic IP to be used in our NAT gateway
resource "aws_eip" "nat_gw_elastic_ip" {
  count = var.vpc_create ? 1 : 0
  vpc   = true
  tags = merge(
    {
      Name = join("-", [terraform.workspace, "nat-eip"])
    },
    var.tags
  )
}

# Create VPC using the official AWS module
module "vpc" {
  count      = var.vpc_create ? 1 : 0
  depends_on = [data.aws_availability_zones.available_azs, aws_eip.nat_gw_elastic_ip]

  source  = "terraform-aws-modules/vpc/aws"
  version = "3.12.0"

  name = join("-", [terraform.workspace, "vpc"])
  cidr = var.vpc_cidr_block
  azs  = data.aws_availability_zones.available_azs.names

  # enable single NAT Gateway to save some money
  # WARNING: this could create a single point of failure, since we are creating a NAT Gateway in one AZ only
  # feel free to change these options if you need to ensure full Availability without the need of running 'terraform apply'
  # reference: https://registry.terraform.io/modules/terraform-aws-modules/vpc/aws/2.44.0#nat-gateway-scenarios
  create_igw           = true
  enable_dns_support   = true
  enable_dns_hostnames = true
  enable_nat_gateway   = true
  single_nat_gateway   = true
  reuse_nat_ips        = true
  external_nat_ip_ids  = [aws_eip.nat_gw_elastic_ip[count.index].id]

  private_subnets = [
    # this loop will create a one-line list as ["10.0.0.0/20", "10.0.16.0/20", "10.0.32.0/20", ...]
    # with a length depending on how many Zones are available
    for zone_id in data.aws_availability_zones.available_azs.zone_ids :
    cidrsubnet(var.vpc_cidr_block, var.subnet_prefix_extension, tonumber(substr(zone_id, length(zone_id) - 1, 1)) - 1)
  ]

  public_subnets = [
    # this loop will create a one-line list as ["10.0.128.0/20", "10.0.144.0/20", "10.0.160.0/20", ...]
    # with a length depending on how many Zones are available
    # there is a zone Offset variable, to make sure no collisions are present with private subnet blocks
    for zone_id in data.aws_availability_zones.available_azs.zone_ids :
    cidrsubnet(var.vpc_cidr_block, var.subnet_prefix_extension, tonumber(substr(zone_id, length(zone_id) - 1, 1)) + var.zone_offset - 1)
  ]

  database_subnets = [
    for zone_id in data.aws_availability_zones.available_azs.zone_ids :
    cidrsubnet(var.vpc_cidr_block, var.subnet_prefix_extension, tonumber(substr(zone_id, length(zone_id) - 1, 1)) + (var.zone_offset / 2) - 1)
  ]

  # add VPC/Subnet tags required by EKS
  tags = merge(
    {
      "kubernetes.io/cluster/${terraform.workspace}" = "shared"
    },
    var.tags
  )
  public_subnet_tags = {
    "kubernetes.io/cluster/${terraform.workspace}" = "shared"
    "kubernetes.io/role/elb"                       = "1"
    Type                                           = "public"
  }
  private_subnet_tags = {
    "kubernetes.io/cluster/${terraform.workspace}" = "shared"
    "kubernetes.io/role/internal-elb"              = "1"
    Type                                           = "private"
  }
}
# TODO add subnet creation if existing VPC used
