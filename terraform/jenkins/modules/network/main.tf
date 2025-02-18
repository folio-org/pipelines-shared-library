locals {
  subnet_count = length(var.availability_zones)
}

# Create a Virtual Private Cloud (VPC) with the specified CIDR block.
# Enables DNS support and hostnames for proper service discovery.
resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-vpc"
  })
}

# Create an Internet Gateway to allow internet access for public instances.
resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-igw"
  })
}

# Create a public subnet in the VPC, enabling automatic public IP assignment.
resource "aws_subnet" "public_subnets" {
  count                   = local.subnet_count
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true # Enable public IP for instances in public subnets.

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-public-subnet-${count.index + 1}"
  })
}

# Create a route table for the public subnet.
resource "aws_route_table" "public_rt" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-public-rt"
  })
}

# Create a default route in the public route table to allow internet access via the Internet Gateway.
resource "aws_route" "public_internet_route" {
  route_table_id         = aws_route_table.public_rt.id
  destination_cidr_block = "0.0.0.0/0" # Route all internet-bound traffic
  gateway_id             = aws_internet_gateway.this.id
}

# Associate the public subnet with the public route table.
resource "aws_route_table_association" "public_rta" {
  count          = local.subnet_count
  subnet_id      = aws_subnet.public_subnets[count.index].id
  route_table_id = aws_route_table.public_rt.id
}

# Allocate an Elastic IP for the NAT Gateway, ensuring a fixed outbound IP.
resource "aws_eip" "nat_eip" {
  domain     = "vpc"
  depends_on = [aws_internet_gateway.this] # Ensure the Internet Gateway is created first.

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-nat-eip"
  })
}

# Deploy a NAT Gateway in the public subnet to enable internet access for private instances.
resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat_eip.id
  subnet_id     = aws_subnet.public_subnets[0].id # NAT Gateway is in the first public subnet.

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-nat"
  })
}

# Create a private subnet where Jenkins-related services will run.
# Private subnets do not assign public IPs.
resource "aws_subnet" "private_subnets" {
  count                   = local.subnet_count
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index + local.subnet_count)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = false # No public IP for private subnets.

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-private-subnet-${count.index + 1}"
  })
}

# Create a route table for the private subnet.
resource "aws_route_table" "private_rt" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-private-rt"
  })
}

# Create a route in the private subnet route table to enable internet access via the NAT Gateway.
resource "aws_route" "private_internet_route" {
  route_table_id         = aws_route_table.private_rt.id
  destination_cidr_block = "0.0.0.0/0"             # Allows all outbound traffic.
  nat_gateway_id         = aws_nat_gateway.this.id # Routes through the NAT Gateway.
}

# Associate the private subnet with the private route table.
resource "aws_route_table_association" "private_rta" {
  count          = local.subnet_count
  subnet_id      = aws_subnet.private_subnets[count.index].id
  route_table_id = aws_route_table.private_rt.id
}