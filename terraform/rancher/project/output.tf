output "okapi_url" {
  value = join("", ["https://", join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "okapi"]), var.root_domain])])
}

output "stripes_url" {
  value = join("", ["https://", join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name]), var.root_domain])])
}

output "pg_password" {
  value = var.pg_password
  sensitive = true
}
output "pg_username" {
  value = var.pg_username
  sensitive = true
}
output "pg_dbname" {
  value = var.pg_dbname
  sensitive = true
}

output "rancher2_project_id" {
  value = rancher2_project.project.id
}
output "rancher2_project_namespace" {
  value = rancher2_namespace.project-namespace.name
}
