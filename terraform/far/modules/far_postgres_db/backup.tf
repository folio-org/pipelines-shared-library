# Define an IAM policy document for assuming the DLM role
data "aws_iam_policy_document" "assume_role" {
  statement {
    effect = "Allow"

    # Allow DLM service to assume this role
    principals {
      type        = "Service"
      identifiers = ["dlm.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "dlm_lifecycle_role" {
  count = var.enable_backups ? 1 : 0
  name = "${var.cluster_name}-${var.namespace_name}-dlm-lifecycle-role"

  assume_role_policy = data.aws_iam_policy_document.assume_role.json

  tags = merge(
    var.tags,
    {
      Name = "${var.cluster_name}-${var.namespace_name}-dlm-lifecycle-role"
    }
  )
}

# Define an IAM policy document for DLM lifecycle management
data "aws_iam_policy_document" "dlm_lifecycle" {
  statement {
    effect = "Allow"

    # Allow DLM to manage EBS snapshots
    actions = [
      "ec2:CreateSnapshot",
      "ec2:CreateSnapshots",
      "ec2:DeleteSnapshot",
      "ec2:DescribeInstances",
      "ec2:DescribeVolumes",
      "ec2:DescribeSnapshots",
      "ec2:DescribeTags",
      "ec2:ListSnapshots",
      "ec2:EnableFastSnapshotRestores",
      "ec2:DescribeFastSnapshotRestores",
      "ec2:DisableFastSnapshotRestores",
      "ec2:CopySnapshot",
      "ec2:ModifySnapshotAttribute",
      "ec2:DescribeSnapshotAttribute"
    ]

    # ⚠ Best Practice: Limit scope to specific EBS volumes if possible
    resources = ["*"]
  }

  statement {
    effect  = "Allow"
    actions = ["ec2:CreateTags"]

    # Allow tagging only on EBS snapshots - fixed ARN format
    resources = ["arn:aws:ec2:*::snapshot/*"]
  }
}

# Create an IAM policy for DLM using the policy document
resource "aws_iam_policy" "dlm_policy" {
  count       = var.enable_backups ? 1 : 0
  name        = "${var.cluster_name}-${var.namespace_name}-dlm-lifecycle-policy"
  description = "IAM policy for Data Lifecycle Manager to manage EBS snapshots"
  policy      = data.aws_iam_policy_document.dlm_lifecycle.json

  tags = merge(var.tags, {
    Name = "${var.cluster_name}-${var.namespace_name}-dlm-lifecycle-policy"
  })
}

# Attach the IAM policies to the DLM role
resource "aws_iam_role_policy_attachment" "dlm_policy_attachment" {
  count       = var.enable_backups ? 1 : 0
  role       = aws_iam_role.dlm_lifecycle_role[0].name
  policy_arn = aws_iam_policy.dlm_policy[0].arn
}

# Define an IAM policy document for snapshot protection
data "aws_iam_policy_document" "snapshot_protection" {
  statement {
    effect = "Deny"

    actions = [
      "ec2:DeleteSnapshot"
    ]

    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/DeleteProtection"
      values   = ["true"]
    }
  }
}

# Policy to prevent deletion of snapshots with DeleteProtection tag
resource "aws_iam_policy" "snapshot_protection_policy" {
  count       = var.enable_backups ? 1 : 0
  name        = "${var.cluster_name}-${var.namespace_name}-snapshot-protection-policy"
  description = "Policy to prevent deletion of EBS snapshots with DeleteProtection tag"
  policy      = data.aws_iam_policy_document.snapshot_protection.json

  tags = merge(var.tags, {
    Name = "${var.cluster_name}-${var.namespace_name}-snapshot-protection-policy"
  })
}

// Attach the snapshot protection policy to the DLM role
resource "aws_iam_role_policy_attachment" "snapshot_protection" {
  count      = var.enable_backups ? 1 : 0
  role       = aws_iam_role.dlm_lifecycle_role[0].name
  policy_arn = aws_iam_policy.snapshot_protection_policy[0].arn
}

resource "aws_dlm_lifecycle_policy" "postgres_backup" {
  count      = var.enable_backups ? 1 : 0
  description        = "DLM lifecycle policy for PostgreSQL EBS volume backups"
  execution_role_arn = aws_iam_role.dlm_lifecycle_role[0].arn
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
