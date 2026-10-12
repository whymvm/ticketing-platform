data "aws_region" "current" {}

output "user_pool_id" {
  description = "Cognito user pool ID."
  value       = aws_cognito_user_pool.main.id
}

output "client_id" {
  description = "App client ID: the audience of the JWT authorizer."
  value       = aws_cognito_user_pool_client.api.id
}

output "issuer" {
  description = "Token issuer that API Gateway validates."
  value       = "https://cognito-idp.${data.aws_region.current.region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}
