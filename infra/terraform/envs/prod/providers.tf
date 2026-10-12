provider "aws" {
  region = var.region

  default_tags {
    tags = {
      project    = "ticketing"
      env        = "prod"
      managed-by = "terraform"
    }
  }
}
