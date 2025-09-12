variable "domain_name" {
  description = "Domain name for the applications"
  type        = string
}

variable "chart_version" {
  description = "Version of the mgr-applications Helm chart"
  type        = string
}

variable "namespace_id" {
  description = "Id of Kubernetes namespace"
  type        = string
}

variable "db_secret_name" {
  description = "Name of the Kubernetes secret containing database credentials"
  type        = string
  sensitive   = true
}

variable "dependencies" {
  description = "List of resources this chart depends on"
  type        = list(any)
  default     = []
}

variable "image_repository" {
  description = "Docker repository for the mgr-applications image"
  type        = string
  default     = "folioorg/mgr-applications"
}

variable "image_tag" {
  description = "Docker tag for the mgr-applications image"
  type        = string
  default     = "3.0.2"
}
