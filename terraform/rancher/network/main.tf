#Get all available AZs in our region
data "aws_availability_zones" "azs" {
  state = "available"
}

# reserve Elastic IP to be used in our NAT gateway
resource "aws_eip" "nat_gw_elastic_ip" {
  vpc = true
  tags = merge(
    {
      Name = join("-", [var.vpc_name, "nat-eip"])
      Region = var.aws_region
      Env    = terraform.workspace
    },
    var.tags
  )
}

# Create VPC using the official AWS module
module "vpc" {
  depends_on = [data.aws_availability_zones.azs, aws_eip.nat_gw_elastic_ip]

  source  = "terraform-aws-modules/vpc/aws"
  version = "3.14.0"

  name = var.vpc_name
  cidr = var.vpc_cidr_block
  azs  = data.aws_availability_zones.azs.names

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
  external_nat_ip_ids  = [aws_eip.nat_gw_elastic_ip.id]

  public_subnets = local.public_subnets

  private_subnets = local.private_subnets

  database_subnets = local.database_subnets

  # add VPC/Subnet tags required by EKS
  tags = var.tags
  public_subnet_tags = merge(
    local.clusters_tags,
    {
      "kubernetes.io/role/elb" = "1"
      Type                     = "public"
      Region = var.aws_region
      Env    = terraform.workspace
  })
  private_subnet_tags = merge(
    local.clusters_tags,
    {
      "kubernetes.io/role/internal-elb" = "1"
      Type                              = "private"
      Region = var.aws_region
      Env    = terraform.workspace
  })
  database_subnet_tags = merge(
    local.clusters_tags,
    {
      Type   = "database"
      Region = var.aws_region
      Env    = terraform.workspace
    }
  )
}
