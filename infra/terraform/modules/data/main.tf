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

resource "aws_dynamodb_table" "orders" {
  name                        = "${var.name_prefix}orders"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "id"
  deletion_protection_enabled = var.deletion_protection

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "expiryShard"
    type = "S"
  }

  attribute {
    name = "expiresAt"
    type = "S"
  }

  # Sparse index of the live reservations: only orders with expiryShard are in it, so it stays small even
  # with millions of finished orders. The shard spreads a big release over several partitions (ADR 0006).
  # It projects every attribute so the expiration job does not read the table again for each order.
  global_secondary_index {
    name            = "expiring-orders"
    projection_type = "ALL"

    key_schema {
      attribute_name = "expiryShard"
      key_type       = "HASH"
    }

    key_schema {
      attribute_name = "expiresAt"
      key_type       = "RANGE"
    }
  }

  point_in_time_recovery {
    enabled = var.point_in_time_recovery
  }
}
