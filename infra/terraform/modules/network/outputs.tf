output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs, one per Availability Zone."
  value       = aws_subnet.public[*].id
}

output "app_security_group_id" {
  description = "Security group of the application tasks."
  value       = aws_security_group.app.id
}

output "vpc_link_security_group_id" {
  description = "Security group of the API Gateway VPC link."
  value       = aws_security_group.vpc_link.id
}
