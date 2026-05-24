variable "project" {
  description = "Project name, used as a prefix for resource names."
  type        = string
  default     = "api-gateway-pilot"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "PostgreSQL database name."
  type        = string
  default     = "apipilot"
}

variable "db_username" {
  description = "PostgreSQL master username."
  type        = string
  default     = "apipilot"
}

variable "image_tag" {
  description = "Container image tag to deploy. The deploy workflow sets this to the git SHA."
  type        = string
  default     = "latest"
}

variable "enable_waf" {
  description = "Attach an AWS WAF web ACL to the load balancer."
  type        = bool
  default     = false
}
