resource "aws_security_group" "alb_sg" {
  name        = "${var.name}-alb-sg"
  description = "Security Group for ALB"
  vpc_id      = var.vpc_id

  # Inbound: Allow HTTP (80) and HTTPS (443) from anywhere
  ingress {
    description      = "Allow HTTP from anywhere"
    from_port        = 80
    to_port          = 80
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
  }
  ingress {
    description      = "Allow HTTPS from anywhere"
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
  }

  # Outbound: to anywhere
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(
    var.tags,
    {
      Name = "${var.name}-alb-sg"
    })
}

resource "aws_lb" "this" {
  name               = "${var.name}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb_sg.id]
  subnets            = [var.subnet_id]

  tags = merge(
    var.tags,
    {
      Name = "${var.name}-alb"
    })
}

resource "aws_lb_target_group" "this" {
  name     = "${var.name}-tg"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = var.vpc_id
  health_check {
    path                = "/login"  # Jenkins login path or "/"
    matcher             = "200"
    unhealthy_threshold = 2
    healthy_threshold   = 2
    interval            = 30
    timeout             = 5
  }

  tags = merge(
    var.tags,
    {
      Name = "${var.name}-jenkins-tg"
    })
}

# Attach the Jenkins instance to the target group
resource "aws_lb_target_group_attachment" "this" {
  target_group_arn = aws_lb_target_group.this.arn
  target_id        = var.jenkins_instance_id
  port             = 8080
}

# Listener for HTTP (port 80) that redirects to HTTPS (port 443)
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

# Listener for HTTPS (port 443) that forwards to the Jenkins Target Group
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.this.arn
  port              = "443"
  protocol          = "HTTPS"
  certificate_arn   = var.certificate_arn
  ssl_policy        = "ELBSecurityPolicy-TLS-1-2-2017-01"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }
}

# Allow the ALB to talk to Jenkins on port 8080
resource "aws_security_group_rule" "alb_to_jenkins" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = var.jenkins_sg_id
  source_security_group_id = aws_security_group.alb_sg.id
}
