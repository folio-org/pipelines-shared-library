output "efs_id" {
  description = "The ID of the EFS file system"
  value       = aws_efs_file_system.this.id
}

output "access_point_id" {
  description = "The ID of the EFS access point"
  value       = aws_efs_access_point.this.id
}

output "k8s_pv_name" {
  description = "Name of the Kubernetes Persistent Volume created"
  value       = kubernetes_persistent_volume.this.metadata[0].name
}

output "k8s_pvc_name" {
  description = "Name of the Kubernetes Persistent Volume Claim created"
  value       = kubernetes_persistent_volume_claim.this.metadata[0].name
}