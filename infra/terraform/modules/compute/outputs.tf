output "cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.main.name
}

output "service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.app.name
}

output "service_discovery_arn" {
  description = "AWS Cloud Map service that API Gateway uses to find the tasks."
  value       = aws_service_discovery_service.app.arn
}

output "log_group_name" {
  description = "Log group of the application."
  value       = aws_cloudwatch_log_group.app.name
}
