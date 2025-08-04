data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

resource "aws_iam_role" "dlm_lifecycle_role" {
  name = "${var.cluster_name}-${var.namespace_name}-dlm-lifecycle-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Principal = {
          Service = "dlm.amazonaws.com"
        },
        Effect = "Allow"
      }
    ]
  })

  tags = merge(
    var.tags,
    {
      Name = "${var.cluster_name}-${var.namespace_name}-dlm-lifecycle-role"
    }
  )
}

// Policy to prevent deletion of snapshots with DeleteProtection tag
resource "aws_iam_policy" "snapshot_protection_policy" {
  name        = "${var.cluster_name}-${var.namespace_name}-snapshot-protection-policy"
  description = "Policy to prevent deletion of EBS snapshots with DeleteProtection tag"

  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Deny",
        Action = [
          "ec2:DeleteSnapshot"
        ],
        Resource = "*",
        Condition = {
          StringEquals = {
            "aws:ResourceTag/DeleteProtection" : "true"
          }
        }
      }
    ]
  })
}

// Attach the snapshot protection policy to all IAM roles that might manage snapshots
resource "aws_iam_role_policy_attachment" "snapshot_protection" {
  role       = aws_iam_role.dlm_lifecycle_role.name
  policy_arn = aws_iam_policy.snapshot_protection_policy.arn
}

resource "aws_iam_role_policy" "dlm_lifecycle" {
  name = "${var.cluster_name}-${var.namespace_name}-dlm-lifecycle-policy"
  role = aws_iam_role.dlm_lifecycle_role.id

  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "ec2:CreateSnapshot",
          "ec2:CreateSnapshots",
          "ec2:DeleteSnapshot",
          "ec2:DescribeInstances",
          "ec2:DescribeVolumes",
          "ec2:DescribeSnapshots",
          "ec2:EnableFastSnapshotRestores",
          "ec2:DescribeFastSnapshotRestores",
          "ec2:DisableFastSnapshotRestores",
          "ec2:CopySnapshot",
          "ec2:ModifySnapshotAttribute",
          "ec2:DescribeSnapshotAttribute"
        ],
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = [
          "ec2:CreateTags"
        ],
        Resource = [
          "arn:aws:ec2:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:snapshot/*",
          "arn:aws:ec2::${data.aws_caller_identity.current.account_id}:snapshot/*",
          "arn:aws:ec2:::snapshot/*"
        ]
      }
    ]
  })
}

resource "aws_dlm_lifecycle_policy" "postgres_backup" {
  description        = "DLM lifecycle policy for PostgreSQL EBS volume backups"
  execution_role_arn = aws_iam_role.dlm_lifecycle_role.arn
  state              = "ENABLED"

  policy_details {
    resource_types = ["VOLUME"]

    schedule {
      name = "Daily PostgreSQL Backups"

      create_rule {
        interval      = 24
        interval_unit = "HOURS"
        times         = ["03:00"] // Take backup at 3 AM UTC
      }

      retain_rule {
        count = 180 // Retain backups for 180 days
      }

      copy_tags = true
    }

    target_tags = {
      Name = "${var.cluster_name}-${var.namespace_name}-postgres-ebs"
    }
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.cluster_name}-${var.namespace_name}-postgres-backup-policy"
    }
  )
}
