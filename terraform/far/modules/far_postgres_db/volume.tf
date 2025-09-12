resource "aws_ebs_volume" "postgres" {
  availability_zone = data.aws_subnet.this.availability_zone
  size              = var.ebs_size
  type              = var.ebs_type
  iops              = var.ebs_iops
  throughput        = var.ebs_throughput

  // Use snapshot_id if provided for restoration
  snapshot_id = var.snapshot_id

  tags = merge(
    var.tags,
    {
      Name                 = "${var.cluster_name}-${var.namespace_name}-postgres-ebs"
      RestoredFromSnapshot = var.snapshot_id != null ? "true" : "false"
      DeleteProtection     = "true"
      Application          = "MGR-FAR"
    }
  )
  lifecycle {
    prevent_destroy = true
  }
}

resource "kubernetes_storage_class" "ebs_gp3" {
  metadata {
    name = "${var.namespace_name}-postgres-sc"
  }

  storage_provisioner = "ebs.csi.aws.com"
  reclaim_policy      = "Retain"
  volume_binding_mode = "WaitForFirstConsumer"
  parameters = {
    type      = var.ebs_type
    fsType    = var.filesystem_type
    encrypted = true
  }
}

resource "kubernetes_persistent_volume" "postgres" {
  metadata {
    name = "${var.namespace_name}-postgres-pv"
    labels = {
      "app.kubernetes.io/name"       = "postgres"
      "app.kubernetes.io/instance"   = var.namespace_name
      "app.kubernetes.io/managed-by" = "terraform"
    }
  }

  spec {
    capacity = {
      storage = format("%dGi", var.ebs_size)
    }

    volume_mode                      = "Filesystem"
    access_modes                     = ["ReadWriteOnce"]
    persistent_volume_reclaim_policy = "Retain"
    storage_class_name               = kubernetes_storage_class.ebs_gp3.metadata[0].name
    persistent_volume_source {
      csi {
        driver        = "ebs.csi.aws.com"
        volume_handle = aws_ebs_volume.postgres.id
        fs_type       = var.filesystem_type
      }
    }
    node_affinity {
      required {
        node_selector_term {
          match_expressions {
            key      = "topology.ebs.csi.aws.com/zone"
            operator = "In"
            values   = [data.aws_subnet.this.availability_zone]
          }
        }
      }
    }
  }
}

resource "kubernetes_persistent_volume_claim" "postgres" {
  metadata {
    name      = "${var.namespace_name}-postgres-pvc"
    namespace = var.namespace_name
    annotations = {
      "pv.kubernetes.io/bind-completed"      = "yes"
      "pv.kubernetes.io/bound-by-controller" = "yes"
    }
  }
  spec {
    access_modes       = ["ReadWriteOnce"]
    storage_class_name = kubernetes_storage_class.ebs_gp3.metadata[0].name
    resources {
      requests = {
        storage = format("%dGi", var.ebs_size)
      }
    }
    volume_name = kubernetes_persistent_volume.postgres.metadata[0].name
  }
}
