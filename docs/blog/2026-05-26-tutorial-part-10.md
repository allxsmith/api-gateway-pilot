---
slug: tutorial-part-10
title: "Part 10: First deploy and teardown"
authors: [asmith]
tags: [tutorial, series]
date: 2026-05-26T10:00
---

The payoff. By the end of Part 10 you'll have the whole prototype running
on AWS at a real `*.cloudfront.net` URL, you'll have signed in to it as
`alice`, and you'll have torn it down with one click to stop the meter.

{/* truncate */}

## The whole journey

| # | What you'll do |
|---|---|
| 1 | Set up the monorepo |
| 2 | A Docusaurus docs site |
| 3 | auth-server |
| 4 | resource-api |
| 5 | nginx reverse proxy |
| 6 | The React SPA |
| 7 | Terraform infrastructure |
| 8 | GitHub Actions — CI and CD |
| 9 | AWS account setup |
| 10 | **First deploy and teardown** *(you are here)* |

## Prerequisites

- Parts 1–9 done.
- `aws sts get-caller-identity` works from your terminal (Part 9).
- `gh auth status` shows you're logged in (Part 9 optional step).

## Step 1 — Push the repo to GitHub

If you haven't already:

```sh
# From the repo root.
gh repo create api-gateway-pilot --public --source=. --remote=origin
git push -u origin main
```

Use `--private` instead of `--public` if you'd rather keep the repo
private — the workflows work either way.

The first push triggers **CI** (`ci.yml`) but not deploy: `deploy.yml` needs
two repo variables (`AWS_DEPLOY_ROLE`, `TF_STATE_BUCKET`) that don't exist
yet. We'll add them after the bootstrap.

## Step 2 — Apply `infra/bootstrap`

This is the one-time setup that creates the Terraform state bucket and the
IAM role GitHub Actions will assume. It uses local state because the
remote state bucket doesn't exist yet (chicken-and-egg).

Edit `infra/bootstrap/variables.tf` first if you haven't — set the
`github_repo` default to **your** `owner/name`:

```hcl
variable "github_repo" {
  default = "yourname/api-gateway-pilot"
}
```

Then apply. The `state_bucket_name` must be **globally unique** across all
of S3 — pick something distinctive.

```sh
cd infra/bootstrap
terraform init
terraform plan -var "state_bucket_name=apipilot-tfstate-$(whoami)-$(date +%s)"
```

Review the plan — you should see:

- 4 × `aws_s3_bucket*` resources (the state bucket + its versioning,
  encryption, and public-access-block).
- 1 × `aws_iam_openid_connect_provider`.
- 1 × `aws_iam_role` (the deploy role).
- 1 × `aws_iam_role_policy_attachment` (AdministratorAccess).
- 1 × `data.tls_certificate` (the GitHub OIDC fingerprint).

If it looks right, apply with the **same** bucket name:

```sh
terraform apply -var "state_bucket_name=<same-name-you-used-above>"
```

Type **`yes`** when prompted. After ~30 seconds it prints the two outputs:

```
Outputs:

deploy_role_arn = "arn:aws:iam::123456789012:role/api-gateway-pilot-github-deploy"
state_bucket    = "apipilot-tfstate-..."
```

Capture both:

```sh
STATE_BUCKET=$(terraform output -raw state_bucket)
DEPLOY_ROLE=$(terraform output -raw deploy_role_arn)
echo "$STATE_BUCKET"
echo "$DEPLOY_ROLE"
```

## Step 3 — Set the GitHub repository variables

Two repo variables wire the workflows to AWS. With `gh`:

```sh
gh variable set AWS_DEPLOY_ROLE --body "$DEPLOY_ROLE"
gh variable set TF_STATE_BUCKET --body "$STATE_BUCKET"

gh variable list
```

Expected:

```
AWS_DEPLOY_ROLE  arn:aws:iam::...:role/api-gateway-pilot-github-deploy
TF_STATE_BUCKET  apipilot-tfstate-...
```

Without `gh`, set them in the GitHub UI under **Settings → Secrets and
variables → Actions → Variables**.

## Step 4 — Enable GitHub Pages

The Docs workflow publishes the Docusaurus site to GitHub Pages, but Pages
has to be turned on first **and** its source set to **GitHub Actions** (not
a branch). One `gh` call:

```sh
gh api -X POST repos/:owner/:repo/pages \
  -f build_type=workflow \
  --silent && echo "Pages enabled"
```

(If Pages was already enabled but pointed at a branch, switch the source:
`gh api -X PUT repos/:owner/:repo/pages -f build_type=workflow`.)

You can also do this in the UI: **Settings → Pages → Build and deployment
→ Source: GitHub Actions**.

## Step 5 — Trigger the Deploy workflow

There are two options.

**Option A**: empty commit + push to `main`.

```sh
cd ../..   # back to repo root
git commit --allow-empty -m "chore: trigger first deploy"
git push
```

**Option B**: manual trigger.

```sh
gh workflow run deploy.yml
```

Watch it:

```sh
gh run watch
```

Or tail the latest run interactively:

```sh
gh run view --log
```

The first deploy takes **8–12 minutes** end-to-end. Most of it is
CloudFront provisioning (5–10 minutes for a brand-new distribution).
Subsequent deploys are quicker — CloudFront just gets invalidated.

The workflow does, in order:

1. OIDC into AWS using the role from bootstrap.
2. `terraform init` against your state bucket.
3. Targeted `terraform apply -target=aws_ecr_repository.this` — the three
   ECR repos.
4. `docker login` + build + push three images (`auth-server`,
   `resource-api`, `nginx`).
5. Full `terraform apply -var image_tag=<sha>` — VPC, RDS, ECS, ALB, S3,
   CloudFront, IAM.
6. SPA build with the live CloudFront URL as the OIDC authority + API
   base.
7. `aws s3 sync` to the SPA bucket + CloudFront invalidation.

The job summary at the end prints the URL:

```
Deployed: https://dXXXXXXXX.cloudfront.net
```

## Step 6 — Open the live SPA

Visit the CloudFront URL in your browser. **It may take a minute or two**
after the workflow finishes — CloudFront propagation is best-effort. If
you see *"The request could not be satisfied"*, wait 60 seconds and
reload.

You should see the **API Gateway Pilot** sign-in card. Click **Sign in**.

The browser redirects to `https://dXXXXXXXX.cloudfront.net/login` —
that's the auth-server's login page, served via the ALB through
CloudFront's `/login` behavior. Sign in:

- **Username**: `alice`
- **Password**: `password`

After redirect-back, you'll land on the dashboard:

- Alice's profile card (Email, Phone, Department, Job title — from the
  seed data in Part 4).
- A **Devices** list with three cards: the two seeded for Alice, plus a
  fresh one for the browser you just signed in from (recorded by the
  dashboard's `POST /api/devices` on mount).

That's the same end-to-end flow you tested locally in Part 6, now serving
from real AWS.

## Step 7 — Tear it down

Don't leave it running. The prototype costs roughly $60–80/mo — most of
it ALB + RDS + Fargate. Run **Teardown**:

```sh
gh workflow run teardown.yml
gh run watch
```

`terraform destroy` removes everything Terraform created:

- ECS services + task definitions.
- ALB + target group + listener.
- RDS instance (no final snapshot — that's `skip_final_snapshot = true`).
- VPC + subnets + IGW + security groups.
- S3 SPA bucket + CloudFront distribution.
- ECR repos (with `force_delete` so the images don't block destroy).

**What survives**: the bootstrap state bucket (a few KB of state, ~$0.01/mo)
and the IAM OIDC provider + deploy role (no cost). They're in a separate
state file and don't get destroyed.

If you want to deploy again later, just push to `main` (or
`gh workflow run deploy.yml`) — the whole stack rebuilds from scratch, and
Flyway re-seeds Alice, Bob, and their devices.

## Day-2 cheat sheet

A few commands you'll keep using:

```sh
# Bring it up
gh workflow run deploy.yml && gh run watch

# Bring it down
gh workflow run teardown.yml && gh run watch

# Check current spend
aws ce get-cost-and-usage \
  --time-period Start=$(date -v-7d +%Y-%m-%d),End=$(date +%Y-%m-%d) \
  --granularity DAILY --metrics UnblendedCost

# Refresh SSO when it expires
aws sso login
```

## What you built

Across ten parts:

- A two-service Spring Boot 3.5 backend with **Spring Authorization
  Server** in front of a legacy `oauth_client_details` table.
- A Vite + React + shadcn/ui SPA doing **Authorization Code + PKCE**.
- An **nginx reverse proxy** in front of both services.
- A Docusaurus docs site with **Redoc API reference** and **Mermaid**.
- **Terraform** for everything — VPC, ECS Fargate + Service Connect, ALB,
  RDS, ECR, S3 + CloudFront, optional WAF.
- **GitHub Actions** with OIDC for CI, deploy, teardown, and docs.

All of it tears down and rebuilds with one click. That's the whole point.

## Where to go from here

The natural next steps if you want to keep going:

- **Custom domain + ACM certificate** on CloudFront (Route53 + an `aws_acm_certificate` in `us-east-1`).
- **Swap auth-server for Cognito** — same OIDC flow, no Spring Authorization Server to maintain.
- **Swap nginx + ALB for API Gateway HTTP API** — fewer Fargate tasks, lower idle cost.
- **Real CI gates** — wire up coverage thresholds, dependency scanning, SBOM publishing.
- **Multi-environment** — duplicate `infra/` for `prod`, give it its own state key (the bucket can hold both `dev/` and `prod/`).

Thanks for following along.
