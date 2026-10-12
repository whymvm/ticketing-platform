provider "aws" {
  region = var.region

  default_tags {
    tags = {
      project    = "ticketing"
      env        = "shared"
      managed-by = "terraform"
    }
  }
}
