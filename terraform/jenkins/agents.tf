resource "aws_security_group" "jenkins_agent" {
  name   = "jenkins_agent"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = var.vpc_private_subnets
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

# resource "aws_instance" "jenkins_agent" {
#   count = var.agents_count
#
#   ami = var.agent_ami
#
#   subnet_id = module.vpc.private_subnets[count.index % length(module.vpc.private_subnets)]
#
#   instance_type = var.agent_instance_type
#
#   vpc_security_group_ids = [aws_security_group.jenkins_agent.id]
#
#   root_block_device {
#     volume_size = 100
#   }
#
#   key_name = var.ssh_key_name
#
#   user_data = templatefile("${path.module}/install_docker.sh", { jenkins_version = var.jenkins_version })
#
#   tags = merge(var.tags,
#     {
#       Name = "folio-jenkins-agent-${count.index + 1}"
#   })
# }

resource "aws_route53_zone_association" "folio_jenkins_vpc" {
  zone_id = var.route53_internal_zone_id
  vpc_id  = module.vpc.vpc_id
}

# resource "aws_route53_record" "jenkins_agent" {
#   count   = var.agents_count
#   zone_id = var.route53_internal_zone_id
#   name    = "folio-jenkins-agent-${count.index + 1}.folio.internal"
#   type    = "A"
#   ttl     = 300
#   records = [aws_instance.jenkins_agent[count.index].private_ip]
# }
