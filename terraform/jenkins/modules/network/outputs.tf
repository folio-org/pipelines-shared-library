output "vpc_id" {
  description = "ID of the new VPC."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets."
  value       = aws_subnet.public_subnets[*].id
}

output "private_subnet_ids" {
  description = "IDs of the private subnets."
  value       = aws_subnet.private_subnets[*].id
}

output "vpc_peering_connection_id" {
  description = "VPC Peering Connection ID between the new VPC and Rancher VPC."
  value       = aws_vpc_peering_connection.this.id
}

output "nat_gateway_id" {
  description = "NAT Gateway ID"
  value       = aws_nat_gateway.this.id
}

output "private_route_table_association_ids" {
  description = "List of private route table association IDs"
  value       = aws_route_table_association.private_rta[*].id
}