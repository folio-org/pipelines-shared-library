output "fqdn" {
  description = "FQDN of the FAR application."
  value = module.far_mgr_app_helm.fqdn_mgr_applications
}

output "db_secret_name" {
  description = "Name of the Kubernetes secret containing database connection information."
  value       = module.far_postgres_helm.db_secret_name
}
