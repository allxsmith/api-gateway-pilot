---
slug: tutorial-part-5
title: "Part 5: nginx reverse proxy"
authors: [asmith]
tags: [tutorial, series]
date: 2026-05-18T10:00
---

A single front door for both services so the React SPA in Part 6 talks to
one origin. Short part, one careful note about proxy headers that — if you
get them wrong — quietly breaks every OAuth redirect.

{/* truncate */}

## The whole journey

| # | What you'll do |
|---|---|
| 1 | Set up the monorepo |
| 2 | A Docusaurus docs site |
| 3 | auth-server — OAuth2 Authorization Server with a legacy client table |
| 4 | resource-api — the resource server |
| 5 | **nginx reverse proxy** *(you are here)* |
| 6 | The React SPA |
| 7 | Terraform infrastructure |
| 8 | GitHub Actions — CI and CD |
| 9 | AWS account setup |
| 10 | First deploy and teardown |

## Prerequisites

- Parts 3 and 4 done — both Spring services run via `make up`.

## What you'll build

- A tiny nginx container that listens on `8088` and routes by path:
  - `/oauth2/*`, `/login`, `/.well-known/*`, `/logout`, `/userinfo`, `/connect/*`
    → auth-server.
  - `/api/*` → resource-api.
  - `/healthz` → a static 200 (used as the AWS load-balancer health probe in
    Part 7).
- `docker-compose.yml` updated to start nginx and have auth-server mint
  tokens under the nginx-fronted URL.

## Step 1 — nginx.conf

Create `nginx/nginx.conf`:

```nginx
# nginx reverse proxy — the single entry point in front of the two services.
# Upstream names (auth-server, resource-api) resolve via Docker Compose
# networking locally, and via ECS Service Connect in AWS.

worker_processes auto;
error_log /dev/stderr warn;

events {
    worker_connections 1024;
}

http {
    access_log /dev/stdout;

    upstream auth_server {
        server auth-server:9000;
    }
    upstream resource_api {
        server resource-api:8080;
    }

    proxy_http_version 1.1;
    # $http_host preserves the port from the original Host header (localhost:8088);
    # $host drops it, which makes Spring rebuild redirects against port 80.
    proxy_set_header Host              $http_host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host  $http_host;
    proxy_set_header X-Forwarded-Port  $server_port;

    server {
        listen 8088;
        server_name _;

        # nginx's own health check (used by the ALB target group in AWS).
        location = /healthz {
            access_log off;
            default_type text/plain;
            return 200 "ok\n";
        }

        # auth-server: OAuth2 / OIDC protocol endpoints and the login page.
        location /oauth2/      { proxy_pass http://auth_server; }
        location /.well-known/ { proxy_pass http://auth_server; }
        location /login        { proxy_pass http://auth_server; }
        location /logout       { proxy_pass http://auth_server; }
        location /userinfo     { proxy_pass http://auth_server; }
        location /connect/     { proxy_pass http://auth_server; }

        # resource-api.
        location /api/ { proxy_pass http://resource_api; }

        location / {
            default_type text/plain;
            return 404 "API Gateway Pilot — no route for this path\n";
        }
    }
}
```

:::caution The $host vs $http_host trap
The trio of `Host`, `X-Forwarded-Host`, and `X-Forwarded-Port` has to be
right or OAuth login will quietly redirect the browser to
`http://localhost/login` — port 80, nothing listening, `ERR_CONNECTION_REFUSED`.

nginx's `$host` is **hostname only — no port**. Spring with
`forward-headers-strategy: framework` rebuilds request URLs from the
forwarded headers; without a port in either `X-Forwarded-Host` or
`X-Forwarded-Port`, it defaults to `80` for `http`.

Use `$http_host` (the original `Host` header verbatim, port included) for
both `Host` and `X-Forwarded-Host`, and add `X-Forwarded-Port $server_port`.
:::

## Step 2 — Dockerfile

Create `nginx/Dockerfile`:

```dockerfile
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 8088
```

## Step 3 — Wire into docker-compose

Two changes to `docker-compose.yml`:

1. Add the `nginx` service.
2. Change **auth-server's `AUTH_ISSUER_URI`** from `http://localhost:9000`
   to `http://localhost:8088` — the nginx-fronted URL. This is the only
   issuer change needed:
   - `CORS_ALLOWED_ORIGINS` on both services already targets the SPA's
     dev-server origin (`http://localhost:5173`) — that doesn't change.
   - `JWK_SET_URI` on resource-api stays as `http://auth-server:9000/...`
     because resource-api reaches auth-server **directly** over the Compose
     network for JWK fetches; only the *browser* talks through nginx.

The full file is now:

```yaml
name: api-gateway-pilot

services:
  postgres:
    image: postgres:16-alpine
    container_name: agp-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-apipilot}
      POSTGRES_USER: ${POSTGRES_USER:-apipilot}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-apipilot}
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-apipilot} -d ${POSTGRES_DB:-apipilot}"]
      interval: 5s
      timeout: 5s
      retries: 10

  auth-server:
    build:
      context: .
      dockerfile: auth-server/Dockerfile
    container_name: agp-auth-server
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${POSTGRES_DB:-apipilot}
      DB_USERNAME: ${POSTGRES_USER:-apipilot}
      DB_PASSWORD: ${POSTGRES_PASSWORD:-apipilot}
      AUTH_SERVER_PORT: 9000
      # Tokens and OIDC metadata are minted under the nginx-fronted URL.
      AUTH_ISSUER_URI: http://localhost:8088
      CORS_ALLOWED_ORIGINS: http://localhost:5173
    ports:
      - "${AUTH_SERVER_PORT:-9000}:9000"
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:9000/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s

  resource-api:
    build:
      context: .
      dockerfile: resource-api/Dockerfile
    container_name: agp-resource-api
    depends_on:
      postgres:
        condition: service_healthy
      auth-server:
        condition: service_healthy
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${POSTGRES_DB:-apipilot}
      DB_USERNAME: ${POSTGRES_USER:-apipilot}
      DB_PASSWORD: ${POSTGRES_PASSWORD:-apipilot}
      RESOURCE_API_PORT: 8080
      JWK_SET_URI: http://auth-server:9000/oauth2/jwks
      CORS_ALLOWED_ORIGINS: http://localhost:5173
    ports:
      - "${RESOURCE_API_PORT:-8080}:8080"
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s

  nginx:
    build:
      context: ./nginx
    container_name: agp-nginx
    depends_on:
      auth-server:
        condition: service_healthy
      resource-api:
        condition: service_healthy
    ports:
      - "${NGINX_PORT:-8088}:8088"
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O- http://localhost:8088/healthz || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 6

volumes:
  pgdata:
```

The `AUTH_ISSUER_URI` change is **important**: the SPA's OIDC library reads
the discovery document from the issuer URL, so the issuer's `/.well-known/openid-configuration`
has to match the URL the browser sees. If auth-server keeps issuing tokens
under `http://localhost:9000` after nginx is in front, the SPA can't
complete the flow.

## Verify

Rebuild and bring everything up:

```sh
make down
make up
make ps
```

After ~60s all four containers should be `(healthy)`. Then:

```sh
curl http://localhost:8088/healthz
# → ok
curl -s http://localhost:8088/.well-known/openid-configuration | jq .issuer
# → "http://localhost:8088"
curl -i http://localhost:8088/api/me | head -3
# → HTTP/1.1 401  (no token)
```

The OIDC discovery doc's `issuer` claim should be the nginx URL, not the
container-internal auth-server URL. If it isn't, recheck
`AUTH_ISSUER_URI`.

## Update local-development.md

Mark nginx as added today, and bump the note:

```md
:::note
Today `make up` starts PostgreSQL, the auth-server, the resource-api, and the
nginx reverse proxy. Reach the backend through nginx at `http://localhost:8088`.
:::
```

## Commit

```sh
git add -A
git commit -m "feat: nginx reverse proxy for unified routing"
```

## What's next

**Part 6 — The React SPA.** A Vite + React + TypeScript app with shadcn/ui
components and `react-oidc-context`, doing real Authorization Code + PKCE
against the auth-server you just built.
