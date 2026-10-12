data "aws_region" "current" {}

locals {
  container_name = "app"
}

resource "aws_cloudwatch_log_group" "app" {
  # checkov:skip=CKV_AWS_158:CloudWatch Logs encrypts log data at rest by default; the logs hold no secrets
  # checkov:skip=CKV_AWS_338:14 days is enough to investigate an incident; longer retention is a cost without a requirement
  name              = "/ecs/${var.name_prefix}app"
  retention_in_days = var.log_retention_days
}

# Container Insights stays off: it adds custom metrics with their own cost. The alarms use the free AWS/ECS metrics
resource "aws_ecs_cluster" "main" {
  # checkov:skip=CKV_AWS_65:Container Insights adds paid custom metrics; the alarms use the free AWS/ECS metrics
  name = "${var.name_prefix}cluster"

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

# ---- Service discovery: API Gateway finds the tasks through AWS Cloud Map ----
# API Gateway needs the IP and the port of each task, so ECS registers SRV records:
# https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html

resource "aws_service_discovery_private_dns_namespace" "main" {
  name = "${trimsuffix(var.name_prefix, "-")}.internal"
  vpc  = var.vpc_id
}

resource "aws_service_discovery_service" "app" {
  name = "app"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.main.id
    routing_policy = "MULTIVALUE"

    dns_records {
      type = "SRV"
      ttl  = 10
    }
  }

  # ECS reports the container health check to Cloud Map, so only healthy tasks receive requests
  health_check_custom_config {}
}

# ---- IAM: one role to start the task, another one for the application ----

data "aws_iam_policy_document" "ecs_tasks_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Execution role: pulls the image, writes the logs and reads the admin key when the task starts
resource "aws_iam_role" "execution" {
  name               = "${var.name_prefix}task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name = "read-admin-key"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "ssm:GetParameters"
      Resource = var.admin_key_parameter_arn
    }]
  })
}

# Task role: only the operations the code calls, only on its own tables and queues. No DeleteItem, no Scan
resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy" "task" {
  name = "tables-and-queues"
  role = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "Tables"
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:Query"]
        Resource = concat(var.table_arns, [for arn in var.table_arns : "${arn}/index/*"])
      },
      {
        Sid      = "Queues"
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage"]
        Resource = var.queue_arns
      },
    ]
  })
}

# ---- Task definition ----

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.name_prefix}app"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  # Graviton is about 20 % cheaper than x86 on Fargate, and the image is already built for ARM64
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  # Writable scratch space for the JVM, so the root file system can be read-only
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([{
    name                   = local.container_name
    image                  = var.container_image
    essential              = true
    readonlyRootFilesystem = true
    portMappings           = [{ containerPort = var.app_port, protocol = "tcp" }]
    mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]

    environment = [for name, value in var.environment : { name = name, value = value }]
    secrets     = [{ name = "ADMIN_API_KEY", valueFrom = var.admin_key_parameter_arn }]

    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:${var.app_port}/actuator/health/readiness || exit 1"]
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = 120 # the JVM starts slowly on 0.25 vCPU
    }

    # Longer than the shutdown of the application (two phases of 15 s), so ECS does not kill it first (ADR 0021).
    # Fargate accepts up to 120 s
    stopTimeout = 35

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = data.aws_region.current.region
        awslogs-stream-prefix = "app"
      }
    }
  }])
}

# ---- Service ----

resource "aws_ecs_service" "app" {
  # checkov:skip=CKV_AWS_333:Without a NAT Gateway the tasks need a public IP to reach the AWS APIs; their security group only accepts the VPC link
  name            = "${var.name_prefix}app"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"
  propagate_tags  = "SERVICE"

  # A new version starts next to the old one, and a failed deployment rolls back by itself:
  # https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-circuit-breaker.html
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [var.security_group_id]
    assign_public_ip = true # only to reach the AWS APIs without a NAT Gateway (see the network module)
  }

  service_registries {
    registry_arn   = aws_service_discovery_service.app.arn
    container_name = local.container_name
    container_port = var.app_port
  }

  # The autoscaling owns the number of tasks after the first apply
  lifecycle {
    ignore_changes = [desired_count]
  }
}

# ---- Autoscaling by CPU, bounded by max_count ----

# desired_count = 0 also sets the maximum to 0: the autoscaling then stops every task (kill switch of the runbook)
resource "aws_appautoscaling_target" "app" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.desired_count
  max_capacity       = var.desired_count == 0 ? 0 : var.max_count
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name_prefix}cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.app.service_namespace
  resource_id        = aws_appautoscaling_target.app.resource_id
  scalable_dimension = aws_appautoscaling_target.app.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value = 70
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
