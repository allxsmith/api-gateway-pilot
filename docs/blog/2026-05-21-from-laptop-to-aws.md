---
slug: from-laptop-to-aws
title: From laptop to AWS
authors: [asmith]
tags: [milestone, architecture]
date: 2026-05-21T21:00
---

The prototype has somewhere to live now. It's all Terraform — a VPC, ECS
Fargate, an ALB, RDS, ECR, CloudFront — and GitHub Actions builds the images
and applies it.

{/* truncate */}

A few decisions worth recording.

**ECS Service Connect for service discovery.** A managed Envoy sidecar, a
namespace on the cluster, a short config block per service. Near-zero cost,
and the services find each other by name in both Compose and ECS so the same
nginx config works in both places.

**No NAT Gateway.** A NAT Gateway is about thirty dollars a month, on the
clock, doing nothing most of the time. The Fargate tasks run in public subnets
with public IPs instead. Security groups still lock things down — tasks only
accept traffic from the load balancer, the database only from the tasks — so
the exposure is contained. It is a deliberate prototype tradeoff worth being
explicit about.

**One origin.** CloudFront serves the React app and also forwards `/api` and
`/oauth2` to the load balancer. The browser only ever talks to one HTTPS
origin, which keeps the whole thing free of CORS headaches and mixed-content
errors.

**Destroy is a feature.** There's no `prevent_destroy` anywhere. `terraform
destroy` tears the whole environment down; the next deploy rebuilds it and
Flyway re-seeds the data. There's a one-click Teardown workflow for exactly
this. Idle, the environment costs almost nothing.

That's the prototype standing up end to end. What's left is documentation —
and writing down the technical patterns that fell out of the build.
