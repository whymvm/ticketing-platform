# One user pool without self sign-up: only an administrator creates users, so the API has exactly the users that were
# invited (the evaluator). The users and their passwords are created with the AWS CLI, never in Terraform, so no
# password reaches the state.

resource "aws_cognito_user_pool" "main" {
  name                = "${var.name_prefix}users"
  deletion_protection = var.deletion_protection ? "ACTIVE" : "INACTIVE"

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  password_policy {
    minimum_length    = 14
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "admin_only"
      priority = 1
    }
  }
}

# Public client (no secret) for the AWS CLI or Postman. Short-lived access tokens; the refresh token renews them
resource "aws_cognito_user_pool_client" "api" {
  name         = "${var.name_prefix}api"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret               = false
  explicit_auth_flows           = ["ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]
  prevent_user_existence_errors = "ENABLED"
  enable_token_revocation       = true

  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 1

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
}
