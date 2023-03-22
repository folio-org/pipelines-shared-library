resource "aws_security_group" "jenkins_master" {
  name   = "jenkins_master"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr_block]
  }
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr_block]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = -1
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = var.tags
}

resource "aws_instance" "jenkins_server" {
  ami = var.ami

  subnet_id = module.vpc.private_subnets[0]

  instance_type = var.instance_type

  vpc_security_group_ids = [aws_security_group.jenkins_master.id]

  root_block_device {
    volume_size = 15
  }

  key_name = var.ssh_key_name

  user_data = file("${path.module}/install_jenkins.sh")

  tags = merge(var.tags,
    {
      Name = "Folio_Jenkins"
  })
}

resource "aws_ebs_volume" "jenkins_home" {
  availability_zone = var.vpc_azs[0]
  size              = var.jenkins_home_size
  tags              = var.tags
}

resource "aws_volume_attachment" "jenkins_home" {
  device_name = "/dev/sdb"
  volume_id   = aws_ebs_volume.jenkins_home.id
  instance_id = aws_instance.jenkins_server.id
}


