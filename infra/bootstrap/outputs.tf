output "state_bucket" {
  description = "Terraform remote-state bucket. Use it in infra/backend.hcl."
  value       = aws_s3_bucket.state.id
}

output "deploy_role_arn" {
  description = "IAM role ARN for GitHub Actions. Set it as the AWS_DEPLOY_ROLE repo variable."
  value       = aws_iam_role.deploy.arn
}
