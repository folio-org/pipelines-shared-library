# This data source fetches the AWS Hosted Zone ID for the ALB.
# The ALB Hosted Zone ID is required for creating an alias record in Route53.
data "aws_lb_hosted_zone_id" "this" {}

resource "aws_route53_record" "this" {
  zone_id = var.zone_id     # The Route53 Hosted Zone where the record will be created.
  name    = var.record_name # The fully qualified domain name (FQDN), e.g., "jenkins.example.com".
  type    = "A"             # Create an A record, which maps the domain to an IP or ALB.

  # Create an alias record pointing to the ALB.
  alias {
    name                   = var.alb_dns_name                   # The DNS name of the ALB.
    zone_id                = data.aws_lb_hosted_zone_id.this.id # Fetch ALB's Hosted Zone ID.
    evaluate_target_health = true                               # Route53 will check the health of the ALB before routing traffic.
  }
}