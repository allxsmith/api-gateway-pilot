---
slug: tutorial-part-9
title: "Part 9: AWS account setup"
authors: [asmith]
tags: [tutorial, series]
date: 2026-05-24T10:00
---

Everything you need on the AWS side before Part 10's first deploy: a fresh
account hardened with root MFA, a `$20/month` budget that emails you before
anything goes sideways, IAM Identity Center for short-lived browser-based
CLI credentials, and the AWS CLI installed cleanly (skipping a macOS
gotcha that bit me when I first tried).

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
| 9 | **AWS account setup** *(you are here)* |
| 10 | First deploy and teardown |

## Prerequisites

- Parts 1–8 done.
- A **credit card** for the AWS account.
- A **password manager** and a **TOTP authenticator app** (e.g. 1Password,
  Authy, Google Authenticator) for root MFA.

## What you'll do

1. Create an AWS account and turn on root MFA.
2. Set up an **AWS Budgets** alert at `$20/month` so you can't be surprised
   by a bill.
3. Enable **IAM Identity Center** and create an admin user (for yourself).
4. Install the **AWS CLI v2** — directly from AWS, not Homebrew.
5. Run `aws configure sso` and verify with `aws sts get-caller-identity`.

By the end you'll be able to run AWS CLI commands from your terminal with
SSO-issued short-lived credentials.

## Step 1 — Create the AWS account

If you don't have one already:

1. Go to **https://aws.amazon.com/** and click **Create an AWS Account**.
2. Use a unique email address — the one you sign in with as root forever.
3. Pick a strong password (your password manager should generate it).
4. Confirm payment details and the phone-verification PIN.
5. Pick the **Basic Support** plan ($0).

You'll land in the AWS Console signed in as the **root user**. From here on,
the root user is for *occasional* admin tasks only — almost everything else
goes through Identity Center.

## Step 2 — Turn on root MFA

The single most important hardening step. Without MFA, a leaked root
password is a full account takeover.

1. Top-right username dropdown → **Security credentials**.
2. **Multi-factor authentication (MFA)** → **Assign MFA device**.
3. **Device name**: e.g. `root-mfa`.
4. **MFA device**: **Authenticator app**.
5. Open your authenticator app, scan the QR code, type two consecutive
   6-digit codes, **Add MFA**.
6. Sign out and sign back in to confirm the prompt works.

While you're in **Security credentials**: if there's any **Access key**
listed under the root user, **delete it**. Long-lived root keys are
indefensible — Identity Center is the modern alternative.

## Step 3 — A `$20/month` budget alarm

AWS Budgets is free and **sends email alerts when forecasted or actual spend
crosses a threshold**. Important detail: a budget **doesn't stop you from
spending money** — it only notifies. Combined with `terraform destroy` from
Part 8, the workflow is: get the alert, hit Teardown.

In the AWS Console:

1. Search **Billing and Cost Management** → open it.
2. Left nav → **Budgets** → **Create budget**.
3. **Budget setup**: choose **Customize (advanced)**.
4. **Budget type**: **Cost budget – Recommended**.
5. **Budget name**: `monthly-cap`.
6. **Period**: **Monthly**. **Budget renewal**: **Recurring**.
7. **Budget amount**: `20.00`. (Adjust to taste — the prototype runs about
   $60–80/mo, so `20` is intentionally low to catch unexpected leaks early.
   If you keep it deployed all month, raise to `100`.)
8. **Alerts**: add two thresholds:
   - **Actual** at **50%** → emails when you've spent $10.
   - **Forecasted** at **100%** → emails when AWS predicts you'll cross $20.
9. **Email recipients**: your address.
10. **Create budget**.

A budget being just an alarm is the surprise here. If you want **hard
billing limits**, those don't exist in AWS by default — the `terraform
destroy` workflow is the real cost cap.

## Step 4 — Enable IAM Identity Center

Identity Center gives you a username and password for the AWS access portal.
The CLI exchanges that login for short-lived credentials whenever you run
`aws configure sso` or `aws sso login`.

1. Console search → **IAM Identity Center** → open.
2. Click **Enable**. The home region defaults to your console region — make
   sure it's the one you'll deploy into (`us-east-1` here).

You'll land on the Identity Center dashboard. Three things to do next:
**create yourself a user**, **create a permission set**, **assign the
permission set to your account**.

### Create your user

1. Left nav → **Users** → **Add user**.
2. Username: e.g. `asmith` (anything memorable — this is your CLI login).
3. Email, first name, last name. Leave the rest defaults.
4. **Send an email to this user with password setup instructions** stays
   checked.
5. **Next** → no groups for now → **Next** → **Add user**.

Check your inbox; click the link; set your password. (Add MFA in the
portal — it'll prompt you. Use the same authenticator app, different entry
from root.)

### Create a permission set

1. Left nav → **Permission sets** → **Create permission set**.
2. **Predefined permission set** → **AdministratorAccess** → **Next**.
3. Name: `AdminAccess`. Session duration: `8 hours`. **Next** → **Create**.

`AdministratorAccess` is broad — fine for a personal prototype where the
only person assuming it is you. Tighten for anything shared.

### Assign the permission set to your account

1. Left nav → **AWS accounts**.
2. Check the box next to your account → **Assign users or groups**.
3. **Users** tab → select your user → **Next**.
4. Select `AdminAccess` → **Next** → **Submit**.

When you sign in to the AWS access portal (the URL on the Identity Center
dashboard — looks like `https://d-xxxxxxxxxx.awsapps.com/start`), you'll
see one tile: your account, with one role: `AdminAccess`. The CLI will use
the same.

**Copy that AWS access portal URL — you'll paste it into `aws configure
sso` in step 6.**

## Step 5 — Install the AWS CLI

The naive `brew install awscli` path is broken on macOS at the moment — a
Homebrew Python ↔ system libexpat mismatch surfaces as:

```
aws: [ERROR]: dlopen(.../pyexpat.cpython-314-darwin.so):
  Symbol not found: _XML_SetAllocTrackerActivationThreshold
```

Don't bother fighting it. Use AWS's **official `.pkg` installer**, which
bundles its own Python:

```sh
curl -L https://awscli.amazonaws.com/AWSCLIV2.pkg -o /tmp/AWSCLIV2.pkg
sudo installer -pkg /tmp/AWSCLIV2.pkg -target /
# or open /tmp/AWSCLIV2.pkg for the GUI installer
```

Verify:

```sh
aws --version
# should print: aws-cli/2.x.x Python/3.x.x Darwin/...
```

The pyexpat gotcha is documented in the
[Troubleshooting](/docs/troubleshooting) page along with two other rough
edges from the local build (Docker Desktop first-launch and the multi-module
Maven Dockerfile).

> **Why not Homebrew for AWS CLI?**
> Brew stays the right tool for Terraform, Node, JDK, Docker Desktop. AWS
> CLI is the outlier on macOS — until brew's Python and the system libexpat
> are back in sync, the official `.pkg` is the steady-state recommendation
> in AWS's own docs anyway.

## Step 6 — `aws configure sso`

```sh
aws configure sso
```

You'll be walked through prompts:

| Prompt | Value |
|---|---|
| SSO session name | `personal` (or any short name) |
| SSO start URL | the AWS access portal URL from Step 4 |
| SSO region | `us-east-1` |
| SSO registration scopes | press Enter for the default |

A browser tab opens asking **"Allow botocore-client-personal to access your
data?"** — that's the CLI's OAuth grant. Confirm.

Back in the terminal it lists your account(s); pick yours, then pick the
`AdminAccess` role. Then:

| Prompt | Value |
|---|---|
| Default client Region | `us-east-1` |
| Default output format | `json` |
| CLI profile name | `default` |

Setting the profile name to `default` means you don't have to type
`AWS_PROFILE=personal` for every command — it just works.

## Step 7 — Verify

```sh
aws sts get-caller-identity
```

Expected output (with your account id and role):

```json
{
    "UserId": "AROAEXAMPLE:asmith",
    "Account": "123456789012",
    "Arn": "arn:aws:sts::123456789012:assumed-role/AWSReservedSSO_AdminAccess_xxxx/asmith"
}
```

Sessions are 8 hours by default. When they expire, run `aws sso login` to
refresh — no need to rerun the full `configure sso`.

## Optional — install the GitHub CLI

Part 10 uses `gh` to set the GitHub repository variables and enable Pages
without leaving the terminal. Install now if you don't have it:

```sh
brew install gh
gh auth login        # interactive: pick HTTPS, log in via browser
```

Verify:

```sh
gh auth status
```

## What's next

**Part 10 — First deploy and teardown.** Apply `infra/bootstrap`, set the
two GitHub repo variables, enable Pages, push to `main`, watch the
**Deploy** workflow build and ship the whole stack to AWS, sign in to the
live SPA, then click **Teardown** to stop paying for it.
