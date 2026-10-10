terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.60"
    }
  }

  # The state path is injected by docker-compose (-backend-config) and kept in a named volume.
  backend "local" {}
}
