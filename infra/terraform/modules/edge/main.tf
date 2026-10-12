# The only public entry point: HTTPS with the AWS certificate of the default endpoint, a JWT on every route, and
# throttling. It reaches the private tasks through a VPC link, without a load balancer:
# https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html

resource "aws_apigatewayv2_api" "main" {
  name          = "${var.name_prefix}api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_vpc_link" "main" {
  name               = "${var.name_prefix}vpc-link"
  subnet_ids         = var.subnet_ids
  security_group_ids = [var.security_group_id]
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id           = aws_apigatewayv2_api.main.id
  name             = "cognito"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    issuer   = var.jwt_issuer
    audience = var.jwt_audience
  }
}

resource "aws_apigatewayv2_integration" "app" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.main.id
  integration_uri        = var.service_discovery_arn
  payload_format_version = "1.0"
  timeout_milliseconds   = 30000 # the maximum for HTTP APIs

  # The customer is the subject of the validated token, not a header the client chooses. The application keeps
  # reading X-Customer-Id, so it does not change, but nobody can impersonate another customer anymore:
  # https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-parameter-mapping.html
  request_parameters = {
    "overwrite:header.X-Customer-Id" = "$context.authorizer.claims.sub"
  }
}

# Only /api/* is published: the actuator endpoints stay private. The scope accepts access tokens only, not ID tokens,
# as API Gateway recommends: https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html
resource "aws_apigatewayv2_route" "api" {
  api_id               = aws_apigatewayv2_api.main.id
  route_key            = "ANY /api/{proxy+}"
  target               = "integrations/${aws_apigatewayv2_integration.app.id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.jwt.id
  authorization_scopes = ["aws.cognito.signin.user.admin"]
}

resource "aws_cloudwatch_log_group" "access" {
  # checkov:skip=CKV_AWS_158:CloudWatch Logs encrypts log data at rest by default; the logs hold no secrets
  # checkov:skip=CKV_AWS_338:14 days is enough to investigate an incident; longer retention is a cost without a requirement
  name              = "/apigateway/${var.name_prefix}api"
  retention_in_days = var.log_retention_days
}

# The $default stage keeps the stage name out of the path, so the application receives /api/... unchanged
resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  # Throttling is the first cost control: it bounds what reaches the tasks and DynamoDB.
  # https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-throttling.html
  default_route_settings {
    throttling_rate_limit  = var.throttling_rate_limit
    throttling_burst_limit = var.throttling_burst_limit
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access.arn
    format = jsonencode({
      requestId       = "$context.requestId"
      ip              = "$context.identity.sourceIp"
      requestTime     = "$context.requestTime"
      routeKey        = "$context.routeKey"
      status          = "$context.status"
      latency         = "$context.responseLatency"
      integration     = "$context.integration.status"
      authorizerError = "$context.authorizer.error"
    })
  }
}
