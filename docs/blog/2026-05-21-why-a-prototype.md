---
slug: why-a-prototype
title: Why a prototype
authors: [asmith]
tags: [prototype, milestone]
date: 2026-05-21T09:00
---

This is a small, runnable prototype of an API gateway stack on AWS. The point
is to have something I can stand up, break, and tear down freely while planning
a real migration toward Amazon API Gateway and Cognito.

{/* truncate */}

The shape is straightforward: a Spring Authorization Server issuing JWTs, a
Spring resource API behind nginx, a React SPA in front, everything deployed
with Terraform. Postgres for storage, ECS Fargate for runtime, CloudFront for
the front end. Nothing exotic.

It is not a production system. It is a sandbox — cheap to run, cheap to
delete, easy to reproduce. Everything on the AWS side is Terraform, so
`destroy` and `apply` are the entire lifecycle. When it is idle, it costs
almost nothing.

I will write short notes here at each milestone as the pieces come together.
First up: the auth server.
