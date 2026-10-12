output "api_id" {
  description = "HTTP API ID."
  value       = aws_apigatewayv2_api.main.id
}

output "api_endpoint" {
  description = "Public HTTPS endpoint of the API."
  value       = aws_apigatewayv2_api.main.api_endpoint
}
