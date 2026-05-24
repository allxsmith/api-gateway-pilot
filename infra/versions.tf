terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state in S3. Configure at init time with the bucket created by
  # infra/bootstrap:
  #   terraform init -backend-config=backend.hcl
  backend "s3" {}
}
