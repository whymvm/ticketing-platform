data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
}

# ---- Remote state of the environments ----
# Versioned, encrypted, private and only reachable over TLS. Locking uses an S3 lock file (use_lockfile), so no
# DynamoDB table is needed: https://developer.hashicorp.com/terraform/language/backend/s3

resource "aws_s3_bucket" "state" {
  # checkov:skip=CKV_AWS_145:SSE-S3 encrypts the state; a KMS key adds cost and key management without a requirement
  # checkov:skip=CKV_AWS_18:Access logs need another bucket; versioning and CloudTrail cover who changed the state
  # checkov:skip=CKV_AWS_144:Cross-region replication doubles the cost; versioning protects the state against mistakes
  # checkov:skip=CKV2_AWS_62:Nothing needs to react to changes of the state file
  bucket = "${var.name_prefix}terraform-state-${local.account_id}"
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Old versions of the state are kept 90 days to recover from a mistake, then deleted
resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"
    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

# ---- Image registry ----
# Shared by every environment: the release pushes an image once and each environment deploys a tag of it.
# Immutable tags, so a tag always means the same image, and a scan of each image when it is pushed.

resource "aws_ecr_repository" "app" {
  # checkov:skip=CKV_AWS_136:ECR encrypts images with AES-256 by default; a KMS key adds cost without a requirement
  name                 = "${var.name_prefix}app"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ---- Release role for GitHub Actions (OIDC, no long-lived keys) ----
# Only the "release" environment of this repository can assume it, and it can only push images to the registry.
# Changing the infrastructure stays a reviewed `terraform apply`.
# https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "release" {
  name = "${var.name_prefix}github-release"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_repository}:environment:release"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "release" {
  name = "push-images"
  role = aws_iam_role.release.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage",
          "ecr:BatchGetImage",
        ]
        Resource = aws_ecr_repository.app.arn
      },
    ]
  })
}

# ---- Account guardrail: monthly budget ----
# Budgets are updated a few times a day, so this is the last line of defense, not the first one: the hard limits
# are the API throttling and the bounded capacity of each environment.

resource "aws_budgets_budget" "monthly" {
  name         = "${var.name_prefix}monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  dynamic "notification" {
    for_each = [50, 80, 100]
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.alert_email]
    }
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}
