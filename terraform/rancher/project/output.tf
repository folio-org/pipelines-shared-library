output "okapi_url" {
  value = join("", ["https://", join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "okapi"]), var.root_domain])])
}

output "stripes_url" {
  value = join("", ["https://", join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name]), var.root_domain])])
}
output "env_type_output" {
  value = var.env_type
}
