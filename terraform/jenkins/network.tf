# reserve Elastic IP to be used in our NAT gateway
resource "aws_eip" "nat_gw_elastic_ip" {
  vpc = true
  tags = merge(
    {
      Name = join("-", [var.vpc_name, "nat-eip"])
    },
    var.tags
  )
}

# Create VPC using the official AWS module
module "vpc" {
  depends_on = [aws_eip.nat_gw_elastic_ip]

  source  = "terraform-aws-modules/vpc/aws"
  version = "3.14.0"

  name = var.vpc_name
  cidr = var.vpc_cidr_block
  azs  = var.vpc_azs

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

  public_subnets = var.vpc_public_subnets

  private_subnets = var.vpc_private_subnets

  # add VPC/Subnet tags required by EKS
  tags = var.tags
  public_subnet_tags = {
    Type = "public"
  }
  private_subnet_tags = {
    Type = "private"
  }
}

data "aws_vpc" "rancher" {
  tags = {
    Name = "folio-rancher-vpc"
  }
}

data "aws_route_table" "rancher_private" {
  tags = {
    Name = "folio-rancher-vpc-private"
  }
}

data "aws_route_table" "rancher_public" {
  tags = {
    Name = "folio-rancher-vpc-public"
  }
}

resource "aws_vpc_peering_connection" "jenkins_rancher" {
  peer_vpc_id = data.aws_vpc.rancher.id
  vpc_id      = module.vpc.vpc_id
  auto_accept = true

  tags = var.tags
}

resource "aws_route" "jenkins_to_rancher_private" {
  route_table_id            = module.vpc.private_route_table_ids[0]
  destination_cidr_block    = data.aws_vpc.rancher.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.jenkins_rancher.id
}

resource "aws_route" "jenkins_to_rancher_public" {
  route_table_id            = module.vpc.public_route_table_ids[0]
  destination_cidr_block    = data.aws_vpc.rancher.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.jenkins_rancher.id
}

resource "aws_route" "rancher_to_jenkins_private" {
  route_table_id            = data.aws_route_table.rancher_private.id
  destination_cidr_block    = var.vpc_cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.jenkins_rancher.id
}

resource "aws_route" "rancher_to_jenkins_public" {
  route_table_id            = data.aws_route_table.rancher_public.id
  destination_cidr_block    = var.vpc_cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.jenkins_rancher.id
}
