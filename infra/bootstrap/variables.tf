variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Project name."
  type        = string
  default     = "api-gateway-pilot"
}

variable "state_bucket_name" {
  description = "Globally-unique name for the Terraform remote-state S3 bucket."
  type        = string
}

variable "github_repo" {
  description = "GitHub repository allowed to assume the deploy role (owner/name)."
  type        = string
  default     = "allxsmith/api-gateway-pilot"
}
