resource "aws_dynamodb_table" "events" {
  name                        = "${var.name_prefix}events"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "id"
  deletion_protection_enabled = var.deletion_protection

  attribute {
    name = "id"
    type = "S"
  }

  point_in_time_recovery {
    enabled = var.point_in_time_recovery
  }

  # No server_side_encryption block on purpose: DynamoDB always encrypts at rest with an AWS-owned key
  # (no KMS cost). A customer-managed key would only be added in AWS if compliance required it.
}
