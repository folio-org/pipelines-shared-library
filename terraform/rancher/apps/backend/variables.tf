variable "rancher_server_url" {
  type        = string
  default     = "https://rancher.dev.folio.org/v3"
  description = "Rancher server URL"
}

variable "rancher_token_key" {
  type        = string
  description = "Rancher token key"
}

variable "env_config" {
  type        = string
  default     = "dev"
  description = "Configuration file for modules"
}

variable "modules_json" {
  type = string
#    default     = "[{\"id\" : \"mod-permissions-6.0.6\", \"action\" : \"enable\"}]" //TODO change to ""
  default     = ""
  description = "Modules list in JSON format"
}

variable "repository" {
  type        = string
  default     = "platform-complete"
  description = "GitHub source repository with modules install JSON"
}
variable "branch" {
  type        = string
  default     = "snapshot"
  description = "GitHub branch of source repository"
}

variable "cluster_name" {
  type        = string
  default     = "folio-testing"
  description = "Name of target cluster"
}

variable "project_name" {
  type        = string
  default     = "sprint"
  description = "Name of target project"
}
