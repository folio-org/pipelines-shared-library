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

# Create an IAM role for AWS Data Lifecycle Manager
resource "aws_iam_role" "dlm_role" {
  name               = "${var.prefix}-jenkins-dlm-role"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-dlm-role"
  })
}

# Define an IAM policy document for snapshot management
data "aws_iam_policy_document" "dlm_lifecycle" {
  statement {
    effect = "Allow"

    # Allow DLM to manage EBS snapshots
    actions = [
      "ec2:CreateSnapshot",
      "ec2:DeleteSnapshot",
      "ec2:DescribeVolumes",
      "ec2:DescribeSnapshots",
      "ec2:DescribeTags",
      "ec2:ListSnapshots"
    ]

    # ⚠ Best Practice: Limit scope to specific EBS volumes if possible
    resources = ["*"]
  }

  statement {
    effect  = "Allow"
    actions = ["ec2:CreateTags"]

    # Allow tagging only on EBS snapshots
    resources = ["arn:aws:ec2:*::snapshot/*"]
  }
}

# Create an IAM policy for DLM
resource "aws_iam_policy" "dlm_policy" {
  name        = "${var.prefix}-jenkins-dlm-policy"
  description = "IAM policy for Data Lifecycle Manager to manage EBS snapshots"
  policy      = data.aws_iam_policy_document.dlm_lifecycle.json

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-dlm-policy"
  })
}

# Attach the IAM policy to the DLM role
resource "aws_iam_role_policy_attachment" "dlm_policy_attachment" {
  policy_arn = aws_iam_policy.dlm_policy.arn
  role       = aws_iam_role.dlm_role.name
}

resource "aws_dlm_lifecycle_policy" "ebs_snapshot_policy" {

  # Description of the lifecycle policy
  description = "${var.prefix}-jenkins-ebs-snapshot-lifecycle"

  # Enable the policy (DLM must be enabled)
  state = "ENABLED"

  # Assign the IAM role that allows snapshot creation
  execution_role_arn = aws_iam_role.dlm_role.arn

  # Define policy details for identifying and managing EBS snapshots
  policy_details {

    # Specify the resource type that this policy applies to (EBS volumes)
    resource_types = ["VOLUME"]

    # Define the tags that should be used to identify the target EBS volumes
    target_tags = {
      "Name" = "${var.prefix}-jenkins-volume"
    }

    # Define a backup schedule
    schedule {

      # Name of the snapshot schedule
      name = "${var.prefix}-jenkins-schedule"

      # Tags to be added to each snapshot created by this policy
      tags_to_add = {
        SnapshotType = "jenkins-backup"
      }

      # Define the frequency of snapshot creation
      create_rule {
        interval      = 24 # Create a snapshot every 24 hours
        interval_unit = "HOURS"
      }

      # Define retention rules for snapshots
      retain_rule {
        interval      = 180 # Retain snapshots for 180 days
        interval_unit = "DAYS"
      }
    }
  }

  # Apply additional tags for tracking and management
  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-ebs-snapshot-policy"
  })
}