terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.60"
    }
  }

  # Applied once per account by a person. Its state stays local: it creates the bucket that stores the state of the
  # environments, so it cannot live in that bucket from the start.
  backend "local" {}
}
