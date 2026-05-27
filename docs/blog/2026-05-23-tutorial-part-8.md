---
slug: tutorial-part-8
title: "Part 8: GitHub Actions — CI and CD"
authors: [asmith]
tags: [tutorial, series]
date: 2026-05-23T10:00
---

Four workflows. **CI** builds and tests on PRs. **Deploy** builds images,
applies Terraform, and publishes the SPA on every push to `main`. **Docs**
publishes the Docusaurus site to GitHub Pages. **Teardown** is the one-click
"stop paying for AWS" button.

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
| 8 | **GitHub Actions — CI and CD** *(you are here)* |
| 9 | AWS account setup |
| 10 | First deploy and teardown |

## Prerequisites

- Parts 1–7 done. The `infra/` Terraform module exists, with the bootstrap
  module ready to apply.
- A **GitHub repository** for this project (you can push to it later — the
  workflows just need to live somewhere).

You still don't need AWS credentials configured. Part 9 sets up the account
and runs the bootstrap; Part 10 fires the first deploy.

## What you'll build

```
.github/workflows/
├── ci.yml         # PR + non-main pushes — build & test everything
├── deploy.yml     # push to main — OIDC into AWS, apply, deploy
├── teardown.yml   # manual — terraform destroy
└── docs.yml       # docs/ changes — build & publish to GitHub Pages
```

All AWS access uses **OIDC**. No long-lived AWS keys are stored in GitHub.

## Step 1 — `ci.yml`

Four parallel jobs: Java, docs, SPA, Terraform.

Create `.github/workflows/ci.yml`:

```yaml
name: CI

# Build and test on pull requests and on pushes to non-main branches.
# Deploys from main are handled by deploy.yml.
on:
  pull_request:
  push:
    branches-ignore:
      - main

jobs:
  java:
    name: Build Java services
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Build and test
        run: mvn -B verify

  docs:
    name: Build documentation site
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: docs
    steps:
      - uses: actions/checkout@v4
      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: docs/package-lock.json
      - name: Install dependencies
        run: npm ci
      - name: Build site
        run: npm run build

  web:
    name: Build SPA
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: web
    steps:
      - uses: actions/checkout@v4
      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: web/package-lock.json
      - name: Install dependencies
        run: npm ci
      - name: Build
        run: npm run build

  terraform:
    name: Validate Terraform
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: hashicorp/setup-terraform@v3
      - name: Format check
        run: terraform fmt -check -recursive infra
      - name: Validate infra
        working-directory: infra
        run: |
          terraform init -backend=false
          terraform validate
      - name: Validate bootstrap
        working-directory: infra/bootstrap
        run: |
          terraform init -backend=false
          terraform validate
```

A few choices to flag:

- `branches-ignore: [main]` on `push` so this doesn't double up with
  `deploy.yml` on `main`. PRs from any branch still run.
- `cache: maven` / `cache: npm` cut a few minutes off subsequent runs.
- `terraform init -backend=false` skips the S3 backend — CI doesn't need
  state, just `validate`.

## Step 2 — `deploy.yml`

This is the long one. The flow:

1. **OIDC into AWS** using the role from `infra/bootstrap`.
2. **Targeted `terraform apply`** for just the ECR repositories — they need
   to exist before `docker push`.
3. **`docker login` + build/push** three images to ECR, tagged with the git
   SHA and `latest`.
4. **Full `terraform apply`** with `image_tag=<sha>` so the ECS services
   pick up the new images.
5. **Build the SPA** with the CloudFront URL as the OIDC authority + API
   base — read straight from Terraform's outputs.
6. **`aws s3 sync` + CloudFront invalidation** to publish.
7. Write the CloudFront URL into the workflow's job summary.

Create `.github/workflows/deploy.yml`:

<details>
<summary>deploy.yml — full pipeline, OIDC → ECR → Terraform → SPA</summary>

```yaml
name: Deploy

# Builds images, applies Terraform, and publishes the SPA.
# Requires two repository variables (Settings -> Variables):
#   AWS_DEPLOY_ROLE  - the IAM role ARN from infra/bootstrap
#   TF_STATE_BUCKET  - the Terraform state bucket from infra/bootstrap
on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  id-token: write
  contents: read

concurrency:
  group: deploy
  cancel-in-progress: false

env:
  AWS_REGION: us-east-1
  TF_STATE_KEY: dev/terraform.tfstate

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Set up Terraform
        uses: hashicorp/setup-terraform@v3

      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Terraform init
        working-directory: infra
        run: |
          terraform init \
            -backend-config="bucket=${{ vars.TF_STATE_BUCKET }}" \
            -backend-config="key=${TF_STATE_KEY}" \
            -backend-config="region=${AWS_REGION}" \
            -backend-config="use_lockfile=true"

      # ECR repositories must exist before images can be pushed.
      - name: Create ECR repositories
        working-directory: infra
        run: terraform apply -auto-approve -target=aws_ecr_repository.this

      - name: Log in to Amazon ECR
        id: ecr
        run: |
          ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
          REGISTRY="${ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com"
          aws ecr get-login-password --region "${AWS_REGION}" \
            | docker login --username AWS --password-stdin "${REGISTRY}"
          echo "registry=${REGISTRY}" >> "$GITHUB_OUTPUT"

      - name: Build and push images
        run: |
          SHA="${GITHUB_SHA::12}"
          REGISTRY="${{ steps.ecr.outputs.registry }}"
          for svc in auth-server resource-api nginx; do
            if [ "$svc" = "nginx" ]; then CONTEXT="nginx"; else CONTEXT="."; fi
            docker build --platform linux/amd64 \
              -f "${svc}/Dockerfile" \
              -t "${REGISTRY}/api-gateway-pilot/${svc}:${SHA}" \
              -t "${REGISTRY}/api-gateway-pilot/${svc}:latest" \
              "${CONTEXT}"
            docker push "${REGISTRY}/api-gateway-pilot/${svc}:${SHA}"
            docker push "${REGISTRY}/api-gateway-pilot/${svc}:latest"
          done

      - name: Terraform apply
        working-directory: infra
        run: terraform apply -auto-approve -var="image_tag=${GITHUB_SHA::12}"

      - name: Build the SPA
        working-directory: web
        run: |
          CF_URL=$(terraform -chdir=../infra output -raw cloudfront_url)
          {
            echo "VITE_OIDC_AUTHORITY=${CF_URL}"
            echo "VITE_API_BASE=${CF_URL}"
          } > .env.production
          npm ci
          npm run build

      - name: Publish the SPA
        working-directory: infra
        run: |
          BUCKET=$(terraform output -raw spa_bucket)
          DIST=$(terraform output -raw cloudfront_distribution_id)
          aws s3 sync ../web/dist "s3://${BUCKET}" --delete
          aws cloudfront create-invalidation --distribution-id "${DIST}" --paths "/*"

      - name: Summary
        working-directory: infra
        run: |
          echo "Deployed: $(terraform output -raw cloudfront_url)" >> "$GITHUB_STEP_SUMMARY"
```

</details>

Things to call out:

- **`permissions: id-token: write`** is required for the OIDC token exchange.
  Without it, `aws-actions/configure-aws-credentials` can't get a token.
- **`vars.AWS_DEPLOY_ROLE` / `vars.TF_STATE_BUCKET`** are GitHub repository
  variables (Settings → Secrets and variables → Actions → Variables). You
  set them in Part 10 once `bootstrap` has produced the role ARN and bucket
  name.
- **`concurrency: group: deploy`** prevents two deploys (or a deploy and a
  teardown) from racing.
- The build of the SPA pulls the CloudFront URL **out of Terraform outputs**.
  The first deploy needs a CloudFront distribution to exist before the SPA
  can be configured — the second `terraform apply` step creates it, then
  the SPA build step reads it back. The first run takes longer because
  CloudFront takes 5–10 minutes to provision; subsequent runs are quick.
- `docker build --platform linux/amd64` is required if you're building from
  an Apple Silicon machine locally — and a no-op on GitHub's Linux x86
  runners. Keeping the flag makes the same command work both places.

## Step 3 — `teardown.yml`

A manual button that runs `terraform destroy`. The whole environment goes
away — ECS, ALB, RDS, S3, CloudFront, the lot. Only the bootstrap state
bucket and OIDC role survive (they live in a separate state). The next
`deploy.yml` run rebuilds from scratch.

Create `.github/workflows/teardown.yml`:

```yaml
name: Teardown

# Destroys the entire AWS environment. Run it manually to stop paying for the
# prototype. The next Deploy run recreates everything from scratch.
on:
  workflow_dispatch:

permissions:
  id-token: write
  contents: read

concurrency:
  group: deploy
  cancel-in-progress: false

env:
  AWS_REGION: us-east-1
  TF_STATE_KEY: dev/terraform.tfstate

jobs:
  teardown:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Set up Terraform
        uses: hashicorp/setup-terraform@v3

      - name: Terraform init
        working-directory: infra
        run: |
          terraform init \
            -backend-config="bucket=${{ vars.TF_STATE_BUCKET }}" \
            -backend-config="key=${TF_STATE_KEY}" \
            -backend-config="region=${AWS_REGION}" \
            -backend-config="use_lockfile=true"

      - name: Terraform destroy
        working-directory: infra
        run: terraform destroy -auto-approve
```

Notice both workflows share the **same concurrency group**. You cannot
accidentally run a teardown while a deploy is in flight (or vice versa).

## Step 4 — `docs.yml`

Publishes the Docusaurus site to GitHub Pages on every push to `main` that
touches `docs/`.

Create `.github/workflows/docs.yml`:

```yaml
name: Docs

# Builds the Docusaurus site and publishes it to GitHub Pages.
on:
  push:
    branches: [main]
    paths:
      - 'docs/**'
      - '.github/workflows/docs.yml'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: docs/package-lock.json
      - name: Install dependencies
        working-directory: docs
        run: npm ci
      - name: Build site
        working-directory: docs
        run: npm run build
      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with:
          path: docs/build
```

```yaml
  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

(Both jobs go in the same file — split into two blocks here so it's easier
to scan.)

A note on the **GitHub Pages source**. Pages can be served from a branch
(legacy) or from **Actions** (modern). For Actions-based Pages, you need to
flip the source in repo Settings → Pages → "Build and deployment" →
**Source: GitHub Actions**. That's a one-time setting; Part 10 covers it via
`gh api`.

## Step 5 — Repository variables (preview)

The two workflows above reference `vars.AWS_DEPLOY_ROLE` and
`vars.TF_STATE_BUCKET`. You don't have values for them yet — `infra/bootstrap`
hasn't been applied. Part 10 walks through:

1. Running `cd infra/bootstrap && terraform apply` (after Part 9 wires up
   AWS credentials).
2. Reading `deploy_role_arn` and `state_bucket` from the bootstrap outputs.
3. Setting them as repo variables with `gh variable set AWS_DEPLOY_ROLE …`.

For now the workflows just *expect* those variables; running `deploy.yml`
without them produces a clear "variable is empty" error.

## Verify

Validate the YAML by syntax (no AWS needed):

```sh
# A clean way to lint a workflow without running it: actionlint.
# Optional — feel free to skip if you don't have it installed.
brew install actionlint
actionlint .github/workflows/*.yml
```

Or just run CI by pushing a branch — `ci.yml`'s four jobs will run and
flag any obvious issue.

## Commit

```sh
git add -A
git commit -m "feat: github actions for ci, deploy, teardown, docs"
```

## What's next

**Part 9 — AWS account setup.** Root MFA, AWS Budgets with a `$20/mo` cap,
IAM Identity Center, the AWS CLI, and `aws configure sso` — everything you
need to actually `terraform apply` from your laptop. Then Part 10 wires the
repo variables and fires the first cloud deploy.
