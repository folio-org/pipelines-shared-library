resource "aws_efs_file_system" "this" {
  creation_token   = var.creation_token
  performance_mode = "generalPurpose"
  encrypted        = var.encrypted

  tags = merge(var.tags, {
    Name = var.name
  })
}

resource "aws_security_group" "efs_sg" {
  count       = var.create_security_group ? 1 : 0
  name        = "${var.name}-sg"
  description = "Allow NFS access to EFS file system ${var.name}"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 2049
    to_port     = 2049
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.name}-sg"
  })
}

locals {
  sg_id = var.security_group_id != "" ? var.security_group_id : (length(aws_security_group.efs_sg) > 0 ? aws_security_group.efs_sg[0].id : "")
}

resource "aws_efs_mount_target" "this" {
  for_each        = toset(var.subnet_ids)
  file_system_id  = aws_efs_file_system.this.id
  subnet_id       = each.value
  security_groups = [local.sg_id]
}

resource "aws_efs_access_point" "this" {
  file_system_id = aws_efs_file_system.this.id

  posix_user {
    uid = var.posix_uid
    gid = var.posix_gid
  }

  root_directory {
    path = var.access_point_root

    creation_info {
      owner_uid   = var.posix_uid
      owner_gid   = var.posix_gid
      permissions = var.access_point_permissions
    }
  }
}