output "okapi_url" {
  value = join("", ["https://", join(".", [join("-", [rancher2_project.project.name, "okapi"]), var.root_domain])])
}

output "stripes_url" {
  value = join("", ["https://", join(".", [terraform.workspace, var.root_domain])])
}
