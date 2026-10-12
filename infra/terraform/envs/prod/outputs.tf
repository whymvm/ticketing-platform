output "api_endpoint" {
  description = "Public HTTPS endpoint. The API is under /api/v1."
  value       = module.edge.api_endpoint
}

output "user_pool_id" {
  description = "Cognito user pool, to create the evaluator user."
  value       = module.auth.user_pool_id
}

output "client_id" {
  description = "Cognito app client, to sign in and get an access token."
  value       = module.auth.client_id
}

output "cluster_name" {
  description = "ECS cluster (runbook: stop or start the service)."
  value       = module.compute.cluster_name
}

output "service_name" {
  description = "ECS service (runbook: stop or start the service)."
  value       = module.compute.service_name
}

output "log_group_name" {
  description = "Log group of the application."
  value       = module.compute.log_group_name
}
