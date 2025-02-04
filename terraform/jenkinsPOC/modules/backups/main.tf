resource "aws_dlm_lifecycle_policy" "ebs_snapshot_policy" {
  description = "${var.name}-ebs-snapshot-lifecycle"
  state       = "ENABLED"
  execution_role_arn = var.dlm_role_arn == "" ? null : var.dlm_role_arn

  # This policy will find volumes with matching tag
  policy_details {
    resource_types = ["VOLUME"]
    target_tags = {
      "Name" = "${var.name}-jenkins-volume"
    }

    schedules {
      name = "${var.name}-schedule"
      tags_to_add = {
        SnapshotType = "jenkins-backup"
      }

      create_rule {
        interval      = 24
        interval_unit = "HOURS"
      }

      retain_rule {
              interval      = 180
              interval_unit = "DAYS"
            }
    }
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.name}-ebs-snapshot-policy"
    })
}
