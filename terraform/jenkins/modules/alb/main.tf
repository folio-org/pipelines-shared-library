# Security Group for the Application Load Balancer (ALB)
resource "aws_security_group" "alb_sg" {
  name        = "${var.prefix}-jenkins-alb-sg"
  description = "Security Group for ALB"
  vpc_id      = var.vpc_id

  # Inbound Rules: Allow HTTP (80) and HTTPS (443) from anywhere
  ingress {
    description = "Allow HTTP from anywhere"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "Allow HTTPS from anywhere"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Outbound Rules: Allow all outgoing traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Tags for resource identification
  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-alb-sg"
  })
}

# Application Load Balancer (ALB) for Jenkins
resource "aws_lb" "this" {
  name               = "${var.prefix}-jenkins-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb_sg.id]
  subnets            = var.subnet_ids # Subnets for ALB placement

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-alb"
  })
}

# Target Group for Jenkins (listens on port 8080)
resource "aws_lb_target_group" "this" {
  name     = "${var.prefix}-jenkins-tg"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = var.vpc_id

  # Health check configuration to verify Jenkins availability
  health_check {
    path                = "/login" # Adjust if necessary
    matcher             = "200-399"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 3
    unhealthy_threshold = 2
  }

  tags = merge(var.tags, {
    Name = "${var.prefix}-jenkins-tg"
  })
}

# Attach Jenkins instance to the target group
resource "aws_lb_target_group_attachment" "this" {
  target_group_arn = aws_lb_target_group.this.arn
  target_id        = var.jenkins_instance_id
  port             = 8080
}

# HTTP Listener: Redirects all HTTP traffic to HTTPS
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# HTTPS Listener: Forwards traffic to the Jenkins target group
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.this.arn
  port              = "443"
  protocol          = "HTTPS"
  certificate_arn   = var.certificate_arn # SSL Certificate for HTTPS
  ssl_policy        = "ELBSecurityPolicy-TLS-1-2-2017-01"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }
}

# Security Group Rule: Allow ALB to communicate with Jenkins on port 8080
resource "aws_security_group_rule" "alb_to_jenkins" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = var.jenkins_sg_id
  source_security_group_id = aws_security_group.alb_sg.id
}