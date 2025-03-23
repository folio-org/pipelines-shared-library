output "yarn_cache_efs_id" {
  description = "EFS ID for Yarn Cache"
  value       = module.yarn_cache.efs_id
}

output "yarn_cache_access_point_id" {
  description = "Access Point ID for Yarn Cache"
  value       = module.yarn_cache.access_point_id
}

output "yarn_cache_k8s_pv_name" {
  description = "Kubernetes PV name for Yarn Cache"
  value       = module.yarn_cache.k8s_pv_name
}

output "yarn_cache_k8s_pvc_name" {
  description = "Kubernetes PVC name for Yarn Cache"
  value       = module.yarn_cache.k8s_pvc_name
}

output "maven_cache_efs_id" {
  description = "EFS ID for Maven Cache"
  value       = module.maven_cache.efs_id
}

output "maven_cache_access_point_id" {
  description = "Access Point ID for Maven Cache"
  value       = module.maven_cache.access_point_id
}

output "maven_cache_k8s_pv_name" {
  description = "Kubernetes PV name for Maven Cache"
  value       = module.maven_cache.k8s_pv_name
}

output "maven_cache_k8s_pvc_name" {
  description = "Kubernetes PVC name for Maven Cache"
  value       = module.maven_cache.k8s_pvc_name
}