output "okapi_url" {
  value = local.okapi_url
}

output "frontend_url" {
  value = local.frontend_url
}

output "saved_to_s3_install_json" {
  value = var.restore_from_saved_s3_install_json ? data.aws_s3_object.saved_to_s3_install_json[0].body : ""
  sensitive = true
}

output "saved_to_s3_okapi_install_json" {
  value = var.restore_from_saved_s3_install_json ? data.aws_s3_object.saved_to_s3_okapi_install_json[0].body : ""
  sensitive = true
}
