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

variable "memory_limit" {
  description = "Memory limit for the application pod"
  type        = string
  default     = "2048Mi"
}

variable "memory_request" {
  description = "Memory request for the application pod"
  type        = string
  default     = "1536Mi"
}

variable "autoscaling_enabled" {
  description = "Enable horizontal pod autoscaling"
  type        = bool
  default     = true
}

variable "autoscaling_min_replicas" {
  description = "Minimum number of pod replicas for autoscaling"
  type        = number
  default     = 1
}

variable "autoscaling_max_replicas" {
  description = "Maximum number of pod replicas for autoscaling"
  type        = number
  default     = 3
}

variable "autoscaling_target_memory_utilization" {
  description = "Target memory utilization percentage for autoscaling"
  type        = number
  default     = 50
}

variable "extra_java_opts" {
  description = "List of additional Java options for the application"
  type        = list(string)
  default = [
    "-Dlogging.level.root=DEBUG -Dsecure_store=AwsSsm -Dsecure_store_props=/usr/ms/aws_ss.properties",
    "-XX:MaxRAMPercentage=70.0"
  ]
}
