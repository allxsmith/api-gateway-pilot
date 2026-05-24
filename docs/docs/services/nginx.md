---
sidebar_position: 3
title: nginx reverse proxy
---

# nginx reverse proxy

A single entry point in front of the two Spring services.

## Routing

| Path | Goes to |
| --- | --- |
| `/oauth2/*`, `/.well-known/*`, `/login`, `/logout`, `/userinfo`, `/connect/*` | auth-server |
| `/api/*` | resource-api |
| `/healthz` | nginx itself (health check) |

Upstream services are found by name — `auth-server` and `resource-api` — which
resolve through Docker Compose networking locally and ECS Service Connect in AWS.

## Why it's here

nginx sits between the load balancer and the backing services so the SPA has a
single backend origin for both authentication and API calls. Locally that
origin is `http://localhost:8088`.

In AWS the same container runs as an ECS Fargate task. It is the ALB's only
target, and forwards to the two services.

## "nginx ingress / egress"

Those are Kubernetes terms and do not apply here — see the
[Architecture overview](../architecture/overview.md). This is a plain reverse
proxy container, not an ingress controller.
