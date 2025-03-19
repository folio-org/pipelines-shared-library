resource "kubernetes_persistent_volume" "this" {
  metadata {
    name = var.k8s_pv_name
  }
  spec {
    capacity = {
      storage = var.pv_capacity
    }
    access_modes                     = ["ReadWriteMany"]
    persistent_volume_reclaim_policy = var.pv_reclaim_policy
    storage_class_name               = var.k8s_storage_class

    persistent_volume_source {
      csi {
        driver        = "efs.csi.aws.com"
        volume_handle = "${aws_efs_file_system.this.id}::${aws_efs_access_point.this.id}"
      }
    }
  }
}

resource "kubernetes_persistent_volume_claim" "this" {
  metadata {
    name      = var.k8s_pvc_name
    namespace = var.k8s_namespace
  }
  spec {
    access_modes       = ["ReadWriteMany"]
    storage_class_name = var.k8s_storage_class
    resources {
      requests = {
        storage = var.pv_capacity
      }
    }
    volume_name = kubernetes_persistent_volume.this.metadata[0].name
  }
}