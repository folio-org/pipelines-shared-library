data "aws_lb_hosted_zone_id" "this" {
  load_balancer_arn = var.alb_dns_name
}

resource "aws_route53_record" "this" {
  zone_id = var.zone_id
  name    = var.record_name
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = data.aws_lb_hosted_zone_id.this.id
    evaluate_target_health = true
  }
}

