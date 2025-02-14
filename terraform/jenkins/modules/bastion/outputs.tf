output "bastion_instance_id" {
  description = "The ID of the Bastion EC2 instance."
  value       = aws_instance.bastion.id
}

output "bastion_private_ip" {
  description = "The private IP address of the Bastion."
  value       = aws_instance.bastion.private_ip
}

output "bastion_public_ip" {
  description = "The public IP address of the Bastion."
  value       = aws_instance.bastion.public_ip
}

output "bastion_sg_id" {
  description = "Security Group ID for the Bastion host."
  value       = aws_security_group.bastion_sg.id
}