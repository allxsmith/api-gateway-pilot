---
sidebar_position: 5
title: Deploying to AWS
---

# Deploying to AWS

The AWS environment is defined entirely in Terraform (`infra/`) and deployed by
GitHub Actions. It is built to be destroyed and recreated freely.

## What you need

For **local development** you only need Docker — see
[Local development](./local-development.md). Nothing below is required to run
the app on your laptop.

To **deploy to AWS** you need, once:

- An AWS account.
- **AWS CLI v2** and the **Terraform CLI**, installed locally.
- AWS credentials with broad permissions, for the one-time bootstrap only.

Install **Terraform** with Homebrew:

```sh
brew install hashicorp/tap/terraform
```

Install the **AWS CLI** with AWS's official installer rather than Homebrew —
it bundles its own Python and avoids the kind of system-library coupling that
periodically breaks the brew bottle (see
[Troubleshooting](./troubleshooting.md)):

```sh
curl -L https://awscli.amazonaws.com/AWSCLIV2.pkg -o /tmp/AWSCLIV2.pkg
sudo installer -pkg /tmp/AWSCLIV2.pkg -target /
# or: open /tmp/AWSCLIV2.pkg   # to use the GUI installer
```

### Authenticate the CLI (IAM Identity Center)

The recommended way to give the AWS CLI credentials is **IAM Identity Center**
— it uses short-lived browser-based tokens instead of long-lived access keys on
disk. For a personal account it's a one-time setup of about ten minutes.

#### In the AWS Console

1. Sign in as your root user (or an existing admin).
2. Search for **IAM Identity Center** and open it.
3. If prompted, click **Enable**. Pick a home region — `us-east-1` matches the
   rest of this prototype.
4. **Users** → **Add user** — enter your email, name, and pick a username.
   You'll receive an email to set your password.
5. **Permission sets** → **Create permission set** → **Predefined** →
   **AdministratorAccess** → name it `AdminAccess` → create.
6. **AWS accounts** → check the account → **Assign users or groups** → select
   your user → select the `AdminAccess` permission set → **Submit**.
7. On the Identity Center dashboard, copy the **AWS access portal URL** — it
   looks like `https://d-xxxxxxxxxx.awsapps.com/start`.
8. Set your password from the email link and sign in to the portal once to
   confirm.

#### In your terminal

```sh
aws configure sso
```

Prompts:

| Prompt | Value |
|---|---|
| SSO session name | `personal` (anything) |
| SSO start URL | the portal URL from step 7 |
| SSO region | `us-east-1` |
| SSO registration scopes | press Enter for the default |

A browser opens — confirm the access. The CLI then lists your account(s) and
roles; pick the account and the `AdminAccess` role. Then:

| Prompt | Value |
|---|---|
| Default region | `us-east-1` |
| Default output format | `json` |
| CLI profile name | `default` (so you don't have to set `AWS_PROFILE`) |

Verify:

```sh
aws sts get-caller-identity
```

Sessions last 8 hours by default; when they expire, `aws sso login` re-opens
the browser.

After the bootstrap, GitHub Actions performs every deploy through OIDC — it
assumes an IAM role with no stored keys, so your laptop never needs AWS
credentials again for routine deploys.

:::tip Why not access keys?
You can also create an IAM user with `AdministratorAccess` and an access key
and run plain `aws configure`. It is simpler, but it puts a long-lived secret
on disk. AWS recommends Identity Center for personal accounts; for this
prototype either works, but the docs steer you toward the better default.
:::

## One-time bootstrap

The bootstrap creates the Terraform state bucket and the GitHub Actions deploy
role:

```bash
cd infra/bootstrap
terraform init
terraform apply -var "state_bucket_name=<a-globally-unique-name>"
```

Note the two outputs.

## Configure the repository

In the GitHub repository, under **Settings → Secrets and variables → Actions →
Variables**, add:

| Variable | Value |
| --- | --- |
| `AWS_DEPLOY_ROLE` | the `deploy_role_arn` bootstrap output |
| `TF_STATE_BUCKET` | the `state_bucket` bootstrap output |

## Deploy

Push to `main`, or run the **Deploy** workflow manually. It:

1. creates the ECR repositories;
2. builds and pushes the three container images;
3. runs `terraform apply` for the full stack;
4. builds the SPA and uploads it to S3 + CloudFront.

The workflow prints the public CloudFront URL when it finishes.

## Tear down

Run the **Teardown** workflow (`workflow_dispatch`). It runs `terraform
destroy` and removes everything. The next Deploy run recreates it from scratch;
Flyway re-seeds the database with demo data.

## Cost

Roughly **$60–80/month** while running, near zero once torn down. Destroy it
when you are not using it — that is the whole point of the Terraform setup.

## Architecture

See the [Architecture overview](./architecture/overview.md) for how the AWS
resources fit together.
