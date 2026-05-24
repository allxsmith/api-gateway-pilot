# infra — Terraform

The AWS environment for the prototype. One root module; resources are grouped
into files by concern (`network.tf`, `data.tf`, `compute.tf`, …).

## What it creates

| File | Resources |
| --- | --- |
| `network.tf` | VPC, two public subnets, internet gateway, security groups |
| `data.tf` | RDS PostgreSQL, DB password in SSM |
| `registry.tf` | ECR repositories for the three images |
| `compute.tf` | ECS Fargate cluster, Service Connect, ALB, three services |
| `frontend.tf` | S3 + CloudFront for the SPA |
| `waf.tf` | AWS WAF web ACL (only when `enable_waf = true`) |

There are no private subnets or NAT Gateway — Fargate tasks run in public
subnets with public IPs, a deliberate cost tradeoff. Security groups still
restrict inbound traffic: tasks accept connections only from the ALB, and RDS
only from the tasks.

## First-time setup

1. Run the [bootstrap](./bootstrap/README.md) once — it creates the state
   bucket and the GitHub deploy role.
2. `cp backend.hcl.example backend.hcl` and set the bucket name.
3. `terraform init -backend-config=backend.hcl`

## Deploy and tear down

Day to day this runs through GitHub Actions (`deploy.yml` / `teardown.yml`).
To run it directly:

```bash
terraform plan
terraform apply
terraform destroy   # tears the whole environment down
```

`terraform destroy` removes everything. The next `apply` (or deploy workflow
run) recreates it; Flyway re-seeds the database with demo data. Only the
bootstrap state bucket and the GitHub deploy role persist.

## Cost

Roughly **$60–80/month** while running (three Fargate tasks, ALB, RDS,
CloudFront). Near zero once destroyed. Tear it down when you are not using it.
