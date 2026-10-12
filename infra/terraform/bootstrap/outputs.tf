output "state_bucket" {
  description = "Bucket for the remote state of the environments (backend.hcl)."
  value       = aws_s3_bucket.state.bucket
}

output "ecr_repository_url" {
  description = "Registry of the application image (container_image of each environment)."
  value       = aws_ecr_repository.app.repository_url
}

output "release_role_arn" {
  description = "Role that the GitHub release workflow assumes with OIDC."
  value       = aws_iam_role.release.arn
}
