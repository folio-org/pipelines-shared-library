# Create a security group to allow controlled access to Jenkins.
resource "aws_security_group" "jenkins_sg" {
  name        = "${var.prefix}-jenkins-sg"
  description = "Security Group for Jenkins EC2"
  vpc_id      = var.vpc_id

  # Outbound Rule: Allow all outgoing traffic (required for updates and plugins)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = -1
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "jenkins" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [aws_security_group.jenkins_sg.id]
  iam_instance_profile   = aws_iam_instance_profile.jenkins_instance_profile.name # Attach IAM Role

  # Root volume settings (20GB, gp3 by default)
  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  # SSH key for access
  key_name = var.ssh_key_name

  # User data script for Jenkins setup
  user_data = templatefile("${path.module}/scripts/user_data.sh", {
    jenkins_version = var.jenkins_version
    backup_bucket   = var.backup_bucket
  })

  # Apply necessary tags to resources
  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins"
  })
}

resource "aws_ebs_volume" "jenkins_data" {
  availability_zone = var.availability_zone
  size              = var.volume_size
  type              = var.volume_type

  # Optionally restore from a snapshot if enabled
  snapshot_id = var.enable_restore ? var.restore_snapshot_id : null

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-volume"
  })
}

# Attach the EBS volume to the Jenkins instance
resource "aws_volume_attachment" "jenkins_data_attachment" {
  device_name  = "/dev/sdf" # Mount device
  volume_id    = aws_ebs_volume.jenkins_data.id
  instance_id  = aws_instance.jenkins.id
  force_detach = true # Allows reattachment if restoring
}

# Define an IAM policy document for assuming the EC2 role
data "aws_iam_policy_document" "assume_role" {
  statement {
    effect = "Allow"

    # Allow EC2 service to assume this role
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

# Create an IAM role for AWS EC2 Instance
resource "aws_iam_role" "jenkins_role" {
  name               = "${var.prefix}-jenkins-server-role"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-server-role"
  })
}

# Define an IAM policy document for accessing S3 backup bucket
data "aws_iam_policy_document" "s3_policy" {
  statement {
    effect = "Allow"

    # Allow EC2 to manage S3 objects
    actions = [
      "s3:ListBucket",
      "s3:GetObject",
      "s3:PutObject"
    ]

    # Limit scope to the backup bucket
    resources = [
      "arn:aws:s3:::${var.backup_bucket}",
      "arn:aws:s3:::${var.backup_bucket}/*"
    ]
  }
}

# Create an IAM policy for Jenkins EC2 to access S3
resource "aws_iam_policy" "jenkins_s3_policy" {
  name        = "${var.prefix}-jenkins-s3-policy"
  description = "IAM policy for Jenkins EC2 to sync data to S3"
  policy      = data.aws_iam_policy_document.s3_policy.json

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-s3-policy"
  })
}

# Attach the S3 policy to the Jenkins role
resource "aws_iam_role_policy_attachment" "jenkins_s3_policy_attach" {
  policy_arn = aws_iam_policy.jenkins_s3_policy.arn
  role       = aws_iam_role.jenkins_role.name
}

# Attach the SSM policy to the Jenkins role
resource "aws_iam_role_policy_attachment" "jenkins_ssm_attach" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
  role       = aws_iam_role.jenkins_role.name
}

# Create an instance profile for Jenkins
resource "aws_iam_instance_profile" "jenkins_instance_profile" {
  name = "${var.prefix}-jenkins-instance-profile"
  role = aws_iam_role.jenkins_role.name
}
