# Bootstrap

Run this once, by hand, before the first deploy. It uses **local state** and
creates two things the rest of the setup depends on:

- the S3 bucket that holds the main Terraform remote state;
- the GitHub Actions OIDC provider and deploy IAM role.

## Apply

```bash
cd infra/bootstrap
terraform init
terraform apply -var "state_bucket_name=<globally-unique-bucket-name>"
```

## After applying

Take the two outputs:

- `state_bucket` → put it in `infra/backend.hcl`.
- `deploy_role_arn` → set it as the `AWS_DEPLOY_ROLE` variable on the GitHub
  repository (Settings → Secrets and variables → Actions → Variables).

The bootstrap state stays local — keep `terraform.tfstate` in this directory (it
is git-ignored). You rarely need to touch it again.
