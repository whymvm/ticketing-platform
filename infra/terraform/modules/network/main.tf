# Two public subnets in two Availability Zones and no NAT Gateway. The tasks get a public IP only to reach the AWS
# APIs (ECR, SQS, CloudWatch, SSM); their security group accepts traffic from the API Gateway VPC link only, so the
# public IP does not open them to the internet. DynamoDB traffic goes through a gateway endpoint, at no cost.

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  zones = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "main" {
  # checkov:skip=CKV2_AWS_11:Flow logs are a paid extra for a VPC with only two flows (VPC link to tasks, tasks to AWS APIs); enable them to investigate
  cidr_block           = var.cidr_block
  enable_dns_support   = true # needed by the private DNS namespace of AWS Cloud Map
  enable_dns_hostnames = true

  tags = { Name = "${var.name_prefix}vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.name_prefix}igw" }
}

resource "aws_subnet" "public" {
  count = length(local.zones)

  vpc_id            = aws_vpc.main.id
  availability_zone = local.zones[count.index]
  cidr_block        = cidrsubnet(var.cidr_block, 8, count.index)

  tags = { Name = "${var.name_prefix}public-${local.zones[count.index]}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.name_prefix}public" }
}

resource "aws_route" "internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

data "aws_region" "current" {}

resource "aws_vpc_endpoint" "dynamodb" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${data.aws_region.current.region}.dynamodb"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]

  tags = { Name = "${var.name_prefix}dynamodb" }
}

# The default security group of the VPC allows nothing
resource "aws_default_security_group" "default" {
  vpc_id = aws_vpc.main.id
}

# ---- Security groups: VPC link -> application, nothing else gets in ----

resource "aws_security_group" "vpc_link" {
  # checkov:skip=CKV2_AWS_5:Attached in the compute and edge modules; Checkov does not follow module outputs
  name        = "${var.name_prefix}vpc-link"
  description = "API Gateway VPC link: only talks to the application"
  vpc_id      = aws_vpc.main.id
}

resource "aws_security_group" "app" {
  # checkov:skip=CKV2_AWS_5:Attached in the compute and edge modules; Checkov does not follow module outputs
  name        = "${var.name_prefix}app"
  description = "Application tasks: only reachable from the VPC link"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_egress_rule" "vpc_link_to_app" {
  security_group_id            = aws_security_group.vpc_link.id
  description                  = "Requests to the application"
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = var.app_port
  to_port                      = var.app_port
}

resource "aws_vpc_security_group_ingress_rule" "app_from_vpc_link" {
  security_group_id            = aws_security_group.app.id
  description                  = "Requests from the API Gateway VPC link"
  referenced_security_group_id = aws_security_group.vpc_link.id
  ip_protocol                  = "tcp"
  from_port                    = var.app_port
  to_port                      = var.app_port
}

resource "aws_vpc_security_group_egress_rule" "app_https" {
  security_group_id = aws_security_group.app.id
  description       = "HTTPS to the AWS APIs (ECR, SQS, DynamoDB, CloudWatch, SSM)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}
