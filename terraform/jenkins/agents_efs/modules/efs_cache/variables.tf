variable "name" {
  description = "Name tag for the EFS file system"
  type        = string
}

variable "creation_token" {
  description = "Creation token for the EFS file system"
  type        = string
}

variable "encrypted" {
  description = "Whether the file system should be encrypted"
  type        = bool
  default     = true
}

variable "vpc_id" {
  description = "The VPC ID where the EFS will be provisioned"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs to create EFS mount targets in"
  type        = list(string)
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access EFS"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "posix_uid" {
  description = "POSIX user ID for the access point"
  type        = number
  default     = 1000
}

variable "posix_gid" {
  description = "POSIX group ID for the access point"
  type        = number
  default     = 1000
}

variable "access_point_root" {
  description = "Root directory for the access point (e.g., '/yarn-cache' or '/maven-cache')"
  type        = string
}

variable "access_point_permissions" {
  description = "Permissions for the access point (e.g., '755')"
  type        = string
  default     = "755"
}

variable "create_security_group" {
  description = "Whether to create a security group for this EFS (set to false if using an existing one)"
  type        = bool
  default     = true
}

variable "security_group_id" {
  description = "Optional existing security group ID to use for EFS"
  type        = string
  default     = ""
}

variable "tags" {
  type        = map(any)
  default     = {}
  description = "Default tags"
}

###########################
# Kubernetes PV/PVC Variables
###########################
variable "k8s_pv_name" {
  description = "Name of the Kubernetes Persistent Volume"
  type        = string
  default     = ""
}

variable "k8s_pvc_name" {
  description = "Name of the Kubernetes Persistent Volume Claim"
  type        = string
  default     = ""
}

# Kubernetes namespace for the PVC
variable "k8s_namespace" {
  description = "Namespace for the Kubernetes PersistentVolumeClaim"
  type        = string
  default     = "default"
}

variable "pv_capacity" {
  description = "Capacity of the persistent volume (e.g., 5Gi)"
  type        = string
  default     = "5Gi"
}

variable "pv_reclaim_policy" {
  description = "Persistent volume reclaim policy (Retain, Delete, Recycle)"
  type        = string
  default     = "Retain"
}

variable "k8s_storage_class" {
  description = "Kubernetes Storage Class name, if any (empty string for none)"
  type        = string
  default     = ""
}