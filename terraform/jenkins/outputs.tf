output "jenkins_ec2_public_dns" {
  description = "Public DNS of Jenkins via ALB (Route53 record)."
  value       = module.route53.fqdn
}
