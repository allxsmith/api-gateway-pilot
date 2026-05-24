---
sidebar_position: 1
slug: /intro
title: Overview
---

# API Gateway Pilot

A small prototype API gateway architecture on AWS, designed to be torn down and
recreated freely while planning a real migration toward **Amazon API Gateway**
and **Amazon Cognito**.

## What it does

A runnable end-to-end stack you can stand up on a laptop or in a personal AWS
account in a few minutes:

- An OAuth2 authorization server (Spring Authorization Server) that issues JWT
  access and refresh tokens with PKCE.
- A resource API that validates those tokens and serves user and device data.
- An nginx reverse proxy that gives the React SPA a single backend origin.
- A React SPA with a login screen and a dashboard.
- Postgres for storage, deployed to AWS with Terraform and GitHub Actions.

## What's in the repo

| Project | What it is |
| --- | --- |
| `auth-server` | Spring Boot OAuth2 Authorization Server — issues JWT tokens, handles login |
| `resource-api` | Spring Boot resource server — serves user and device information |
| `nginx` | Reverse proxy — one entry point in front of both services |
| `web` | React single-page app — login and dashboard |
| `docs` | This documentation site |
| `infra` | Terraform for the AWS environment |

## Where to go next

- **[Architecture](./architecture/overview.md)** — how the pieces fit together.
- **[Local development](./local-development.md)** — run the stack on your
  machine.
- **[Deploying to AWS](./deploying-to-aws.md)** — bring it up in your AWS
  account.
- **[Blog](/blog)** — build notes from putting it together.

:::tip Where this is heading
The prototype is complete and runs end to end. The next step — out of scope
here — is replacing the auth-server with **Amazon Cognito** and the nginx/ALB
front with **Amazon API Gateway**.
:::
