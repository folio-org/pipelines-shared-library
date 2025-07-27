output "ebs_volume_id" {
  description = "ID of the AWS EBS volume backing the PostgreSQL database."
  value       = aws_ebs_volume.postgres.id
}

output "persistent_volume_name" {
  description = "Name of the Kubernetes PersistentVolume."
  value       = kubernetes_persistent_volume.postgres.metadata[0].name
}

output "persistent_volume_claim_name" {
  description = "Name of the PersistentVolumeClaim used by the PostgreSQL release."
  value       = kubernetes_persistent_volume_claim.postgres.metadata[0].name
}

output "db_secret_name" {
  description = "Name of the Kubernetes secret containing database connection information."
  value       = rancher2_secret_v2.postgres_credentials.name
}
