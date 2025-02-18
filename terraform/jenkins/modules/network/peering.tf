# Get details of the existing Rancher VPC using its name tag.
data "aws_vpc" "rancher_vpc" {
  tags = {
    Name = "folio-rancher-vpc"
  }
}

# Get the private route table associated with the Rancher VPC.
data "aws_route_table" "rancher_private_rt" {
  tags = {
    Name = "folio-rancher-vpc-private"
  }
}

# Get the public route table associated with the Rancher VPC.
data "aws_route_table" "rancher_public_rt" {
  tags = {
    Name = "folio-rancher-vpc-public"
  }
}

# Establish a VPC peering connection between the Jenkins VPC and the Rancher VPC.
resource "aws_vpc_peering_connection" "this" {
  vpc_id      = aws_vpc.this.id             # Jenkins VPC ID
  peer_vpc_id = data.aws_vpc.rancher_vpc.id # Rancher VPC ID
  auto_accept = true                        # Automatically accept the connection request

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-peering"
  })
}

# Accept the peering request from the Rancher VPC side.
resource "aws_vpc_peering_connection_accepter" "rancher_accepts" {
  depends_on                = [aws_vpc_peering_connection.this]
  vpc_peering_connection_id = aws_vpc_peering_connection.this.id
  auto_accept               = true # Ensure auto-acceptance of the peering request.

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-peering-accepter"
  })
}

# Add a route in the Jenkins public route table to send traffic to the Rancher VPC.
resource "aws_route" "jenkins_public_to_rancher" {
  route_table_id            = aws_route_table.public_rt.id
  destination_cidr_block    = data.aws_vpc.rancher_vpc.cidr_block # Target VPC CIDR
  vpc_peering_connection_id = aws_vpc_peering_connection.this.id
  depends_on                = [aws_vpc_peering_connection_accepter.rancher_accepts]
}

# Add a route in the Jenkins private route table to send traffic to the Rancher VPC.
resource "aws_route" "jenkins_private_to_rancher" {
  route_table_id            = aws_route_table.private_rt.id
  destination_cidr_block    = data.aws_vpc.rancher_vpc.cidr_block # Target VPC CIDR
  vpc_peering_connection_id = aws_vpc_peering_connection.this.id
  depends_on                = [aws_vpc_peering_connection_accepter.rancher_accepts]
}

# Add a route in the Rancher private route table to send traffic to the Jenkins VPC.
resource "aws_route" "rancher_private_to_jenkins_vpc" {
  route_table_id            = data.aws_route_table.rancher_private_rt.id
  destination_cidr_block    = var.vpc_cidr # Jenkins VPC CIDR
  vpc_peering_connection_id = aws_vpc_peering_connection.this.id
  depends_on                = [aws_vpc_peering_connection_accepter.rancher_accepts]
}

# Add a route in the Rancher public route table to send traffic to the Jenkins VPC.
resource "aws_route" "rancher_public_to_jenkins_vpc" {
  route_table_id            = data.aws_route_table.rancher_public_rt.id
  destination_cidr_block    = var.vpc_cidr # Jenkins VPC CIDR
  vpc_peering_connection_id = aws_vpc_peering_connection.this.id
  depends_on                = [aws_vpc_peering_connection_accepter.rancher_accepts]
}