provider "aws" {
  region = var.region

  # Dummy credentials accepted by LocalStack; they are not secrets.
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    dynamodb = var.localstack_endpoint
    sqs      = var.localstack_endpoint
  }

  default_tags {
    tags = {
      project    = "ticketing"
      env        = "local"
      managed-by = "terraform"
    }
  }
}
