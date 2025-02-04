resource "aws_security_group" "jenkins_sg" {
  name        = "${var.name}-jenkins-sg"
  description = "Security Group for Jenkins EC2"
  vpc_id      = var.vpc_id

  # Outbound for internet / updates
  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "jenkins" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [aws_security_group.jenkins_sg.id]

  # Root volume settings (optional tuning)
  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  user_data = templatefile("${path.module}/scripts/user_data.sh", {
    jenkins_version = var.jenkins_version
    jenkins_plugins = var.jenkins_plugins
    backup_bucket   = var.backup_bucket
  })

  tags = merge(
    var.tags,
    {
      Name = "${var.name}-jenkins"
  })
}

# EBS Volume (either new or from snapshot if restore is enabled)
resource "aws_ebs_volume" "jenkins_data" {
  availability_zone = var.availability_zone
  size              = var.volume_size
  type              = var.volume_type
  snapshot_id       = var.enable_restore ? var.restore_snapshot_id : null

  tags = merge(
    var.tags,
    {
      Name = "${var.name}-jenkins-volume"
  })
}

# Attach volume to EC2 instance
resource "aws_volume_attachment" "jenkins_data_attachment" {
  device_name  = "/dev/sdf"
  volume_id    = aws_ebs_volume.jenkins_data.id
  instance_id  = aws_instance.jenkins.id
  force_detach = true
  # NOTE: force_detach is true to allow re-attaching if we run a restore
}