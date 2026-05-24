# ECS Fargate cluster, the three services, and the Application Load Balancer.
# Service-to-service discovery uses ECS Service Connect; nginx is the only ALB
# target and forwards to the others.

# --- Cluster and Service Connect namespace ---

resource "aws_service_discovery_http_namespace" "main" {
  name = local.name
}

resource "aws_ecs_cluster" "main" {
  name = local.name

  service_connect_defaults {
    namespace = aws_service_discovery_http_namespace.main.arn
  }
}

resource "aws_cloudwatch_log_group" "this" {
  for_each = toset(local.services)

  name              = "/ecs/${local.name}/${each.key}"
  retention_in_days = 7
}

# --- IAM ---

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${local.name}-task-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Lets ECS read the SecureString database password from SSM.
resource "aws_iam_role_policy" "task_execution_secrets" {
  name = "ssm-secrets"
  role = aws_iam_role.task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameters"]
        Resource = [aws_ssm_parameter.db_password.arn]
      },
      {
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = ["*"]
      }
    ]
  })
}

# Task role — the applications make no AWS API calls, so it has no policies.
resource "aws_iam_role" "task" {
  name               = "${local.name}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

# --- Shared container settings ---

locals {
  db_environment = [
    { name = "DB_HOST", value = aws_db_instance.main.address },
    { name = "DB_PORT", value = "5432" },
    { name = "DB_NAME", value = var.db_name },
    { name = "DB_USERNAME", value = var.db_username },
  ]

  db_secrets = [
    { name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
  ]

  public_url = "https://${aws_cloudfront_distribution.main.domain_name}"
}

# --- auth-server ---

resource "aws_ecs_task_definition" "auth_server" {
  family                   = "${local.name}-auth-server"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([{
    name      = "auth-server"
    image     = "${aws_ecr_repository.this["auth-server"].repository_url}:${var.image_tag}"
    essential = true
    portMappings = [{
      name          = "auth"
      containerPort = 9000
      protocol      = "tcp"
      appProtocol   = "http"
    }]
    environment = concat(local.db_environment, [
      { name = "AUTH_SERVER_PORT", value = "9000" },
      { name = "AUTH_ISSUER_URI", value = local.public_url },
      { name = "CORS_ALLOWED_ORIGINS", value = local.public_url },
      { name = "SPA_REDIRECT_URIS", value = "${local.public_url}/callback" },
    ])
    secrets = local.db_secrets
    healthCheck = {
      command     = ["CMD-SHELL", "curl -fsS http://localhost:9000/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 5
      startPeriod = 120
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.this["auth-server"].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "auth-server"
      }
    }
  }])
}

resource "aws_ecs_service" "auth_server" {
  name            = "auth-server"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.auth_server.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
    service {
      port_name      = "auth"
      discovery_name = "auth-server"
      client_alias {
        port     = 9000
        dns_name = "auth-server"
      }
    }
  }
}

# --- resource-api ---

resource "aws_ecs_task_definition" "resource_api" {
  family                   = "${local.name}-resource-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([{
    name      = "resource-api"
    image     = "${aws_ecr_repository.this["resource-api"].repository_url}:${var.image_tag}"
    essential = true
    portMappings = [{
      name          = "resource"
      containerPort = 8080
      protocol      = "tcp"
      appProtocol   = "http"
    }]
    environment = concat(local.db_environment, [
      { name = "RESOURCE_API_PORT", value = "8080" },
      { name = "JWK_SET_URI", value = "http://auth-server:9000/oauth2/jwks" },
      { name = "CORS_ALLOWED_ORIGINS", value = local.public_url },
    ])
    secrets = local.db_secrets
    healthCheck = {
      command     = ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 5
      startPeriod = 120
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.this["resource-api"].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "resource-api"
      }
    }
  }])
}

resource "aws_ecs_service" "resource_api" {
  name            = "resource-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.resource_api.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
    service {
      port_name      = "resource"
      discovery_name = "resource-api"
      client_alias {
        port     = 8080
        dns_name = "resource-api"
      }
    }
  }
}

# --- nginx (the only ALB target) ---

resource "aws_ecs_task_definition" "nginx" {
  family                   = "${local.name}-nginx"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([{
    name      = "nginx"
    image     = "${aws_ecr_repository.this["nginx"].repository_url}:${var.image_tag}"
    essential = true
    portMappings = [{
      name          = "nginx"
      containerPort = 8088
      protocol      = "tcp"
      appProtocol   = "http"
    }]
    healthCheck = {
      command     = ["CMD-SHELL", "wget -q -O- http://localhost:8088/healthz || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 15
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.this["nginx"].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "nginx"
      }
    }
  }])
}

resource "aws_ecs_service" "nginx" {
  name            = "nginx"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.nginx.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  health_check_grace_period_seconds = 120

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.nginx.arn
    container_name   = "nginx"
    container_port   = 8088
  }

  # nginx is a Service Connect client — it resolves auth-server and resource-api.
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
  }

  depends_on = [aws_lb_listener.http]
}

# --- Application Load Balancer ---

resource "aws_lb" "main" {
  name               = "${local.name}-alb"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id
}

resource "aws_lb_target_group" "nginx" {
  name        = "${local.name}-nginx"
  port        = 8088
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/healthz"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.nginx.arn
  }
}
