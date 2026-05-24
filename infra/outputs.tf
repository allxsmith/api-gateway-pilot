output "cloudfront_url" {
  description = "Public URL of the prototype (SPA + API)."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution id (used for cache invalidation)."
  value       = aws_cloudfront_distribution.main.id
}

output "spa_bucket" {
  description = "S3 bucket the SPA build is uploaded to."
  value       = aws_s3_bucket.spa.id
}

output "alb_dns_name" {
  description = "Internal-facing ALB DNS name."
  value       = aws_lb.main.dns_name
}

output "ecr_repository_urls" {
  description = "ECR repository URLs for each service image."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "db_endpoint" {
  description = "RDS endpoint address."
  value       = aws_db_instance.main.address
}
