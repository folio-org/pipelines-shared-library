output "instance_id" {
  value = aws_instance.jenkins.id
}

output "security_group_id" {
  value = aws_security_group.jenkins_sg.id
}

output "ebs_volume_id" {
  value = aws_ebs_volume.jenkins_data.id
}