output "okapi_url" {
  value = local.okapi_url
}

output "frontend_url" {
  value = local.frontend_url
}

output "custom_install_json" {
  value = var.restore_postgresql_from_backup ? data.aws_s3_object.custom_install_json[0].body : ""
  sensitive = true
}

output "custom_okapi_install_json" {
  value = var.restore_postgresql_from_backup ? data.aws_s3_object.custom_okapi_install_json[0].body : ""
  sensitive = true
}

output "custom_platform_complete_tag" {
  value = var.restore_postgresql_from_backup ? data.aws_s3_object.custom_platform_complete_tag[0].body : ""
  sensitive = true
}
