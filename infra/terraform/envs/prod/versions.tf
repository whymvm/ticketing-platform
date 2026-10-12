terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.60"
    }
  }

  # Remote state in the bucket of the bootstrap stack, locked with an S3 lock file. The bucket name is given at
  # init time: terraform init -backend-config=backend.hcl
  backend "s3" {
    key          = "envs/prod/terraform.tfstate"
    encrypt      = true
    use_lockfile = true
  }
}
