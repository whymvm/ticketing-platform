# Production environment. It reuses the data and messaging modules of the local environment, so both environments
# have the same tables, index and queues; only the variables and the state change.

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  app_port                = 8080
  admin_key_parameter_arn = "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter${var.admin_key_parameter_name}"
}

module "data" {
  source = "../../modules/data"

  name_prefix            = var.name_prefix
  point_in_time_recovery = true
  deletion_protection    = var.deletion_protection

  # Far above what the throttled API can send, and a hard limit if something goes wrong
  max_request_units = { read = 100, write = 50 }
}

module "messaging" {
  source = "../../modules/messaging"

  name_prefix = var.name_prefix
}

module "network" {
  source = "../../modules/network"

  name_prefix = var.name_prefix
  app_port    = local.app_port
}

module "compute" {
  source = "../../modules/compute"

  name_prefix             = var.name_prefix
  vpc_id                  = module.network.vpc_id
  subnet_ids              = module.network.public_subnet_ids
  security_group_id       = module.network.app_security_group_id
  container_image         = var.container_image
  app_port                = local.app_port
  desired_count           = var.desired_count
  admin_key_parameter_arn = local.admin_key_parameter_arn

  # The same variables that docker-compose gives the application locally. Without AWS_ENDPOINT the SDK uses AWS
  environment = {
    AWS_REGION             = data.aws_region.current.region
    EVENTS_TABLE           = module.data.events_table_name
    ORDERS_TABLE           = module.data.orders_table_name
    ORDER_HISTORY_TABLE    = module.data.order_history_table_name
    IDEMPOTENCY_TABLE      = module.data.idempotency_table_name
    QUEUE_RESERVATIONS_URL = module.messaging.queue_urls["reservations"]
  }

  table_arns = [
    module.data.events_table_arn,
    module.data.orders_table_arn,
    module.data.order_history_table_arn,
    module.data.idempotency_table_arn,
  ]
  queue_arns = [module.messaging.queue_arns["reservations"]]
}

module "auth" {
  source = "../../modules/auth"

  name_prefix         = var.name_prefix
  deletion_protection = var.deletion_protection
}

module "edge" {
  source = "../../modules/edge"

  name_prefix            = var.name_prefix
  subnet_ids             = module.network.public_subnet_ids
  security_group_id      = module.network.vpc_link_security_group_id
  service_discovery_arn  = module.compute.service_discovery_arn
  jwt_issuer             = module.auth.issuer
  jwt_audience           = [module.auth.client_id]
  throttling_rate_limit  = var.throttling_rate_limit
  throttling_burst_limit = var.throttling_burst_limit
}

module "observability" {
  source = "../../modules/observability"

  name_prefix  = var.name_prefix
  alert_email  = var.alert_email
  api_id       = module.edge.api_id
  cluster_name = module.compute.cluster_name
  service_name = module.compute.service_name
  queue_name   = module.messaging.queue_names["reservations"]
  dlq_name     = module.messaging.dlq_names["reservations"]
}
