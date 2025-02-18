variable "zone_id" {
  type        = string
  description = "Route53 Hosted Zone ID."
}

variable "record_name" {
  type        = string
  description = "DNS record name (e.g. jenkins.example.com)."
}

variable "alb_dns_name" {
  type        = string
  description = "DNS name of the ALB."
}