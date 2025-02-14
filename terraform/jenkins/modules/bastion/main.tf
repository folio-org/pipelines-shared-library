# Security Group for the Bastion Host
resource "aws_security_group" "bastion_sg" {
  name        = "${var.prefix}-bastion-sg"
  description = "Security group for the Bastion host"
  vpc_id      = var.vpc_id

  # Inbound Rule: Allow SSH (port 22) access from specified CIDRs
  ingress {
    description = "SSH access"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidrs # List of allowed IP ranges
  }

  # Outbound Rule: Allow all outgoing traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Tags for identifying the security group
  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-bastion-sg"
  })
}

# Bastion Host EC2 Instance
resource "aws_instance" "bastion" {
  ami                    = var.ami_id                         # AMI ID for the Bastion instance
  instance_type          = var.instance_type                  # Instance type (e.g., t3.micro, t2.medium)
  subnet_id              = var.subnet_id                      # Public subnet ID where the Bastion host resides
  vpc_security_group_ids = [aws_security_group.bastion_sg.id] # Associate with the Bastion security group

  # EC2 Key Pair for SSH access
  key_name = var.key_pair_name

  # Root block device configuration
  root_block_device {
    volume_type = "gp3" # General Purpose SSD (gp3)
    volume_size = 10    # Root disk size in GB
  }

  # Tags for the Bastion instance
  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-bastion"
  })
}

# Security Group Rule: Allow Bastion Host to SSH into Jenkins
resource "aws_security_group_rule" "bastion_to_jenkins_ssh" {
  type                     = "ingress"
  from_port                = 22 # SSH port
  to_port                  = 22 # SSH port
  protocol                 = "tcp"
  security_group_id        = var.jenkins_sg_id                # Target Jenkins security group
  source_security_group_id = aws_security_group.bastion_sg.id # Source: Bastion security group

  description = "Allow SSH from Bastion to Jenkins"
}