resource "aws_security_group" "bastion" {
  name   = "bastion"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = -1
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = var.tags
}

resource "aws_instance" "bastion" {
  ami = var.ami

  subnet_id = module.vpc.public_subnets[0]

  instance_type = "t2.micro"

  vpc_security_group_ids = [aws_security_group.bastion.id]

  key_name = var.ssh_key_name

  tags = merge(var.tags,
    {
      Name = "Bastion"
  })
}
