---
sidebar_position: 4
title: Local development
---

# Local development

The whole stack is designed to run on your machine before any AWS spend.

## Prerequisites

- **JDK 21** and **Maven 3.9+**
- **Node.js 20+**
- **Docker Desktop** — for the local stack. On first install, **launch the app
  once** (`open -a Docker`) so it sets up the `docker compose` plugin.

Stuck? See [Troubleshooting](./troubleshooting.md).

## Clone and configure

```bash
git clone https://github.com/allxsmith/api-gateway-pilot.git
cd api-gateway-pilot
cp .env.example .env
```

The `.env` file holds local-only defaults (database name, ports). It is
git-ignored.

## Start the stack

```bash
make up      # start the containers
make ps      # check status
make logs    # tail logs
make down    # stop
```

Run `make help` to list every target.

## What runs today

The local stack grows as each service is built:

| Phase | Added to the local stack |
| --- | --- |
| 0 | PostgreSQL |
| 2 | `auth-server` |
| 3 | `resource-api` |
| 4 | nginx reverse proxy |

:::note
This page is revised as each service joins the stack. Today `make up` starts
PostgreSQL, the auth-server, the resource-api, and the nginx reverse proxy.
Reach the backend through nginx at `http://localhost:8088`.
:::

## The web app

The React SPA runs on the Vite dev server, separately from the Docker stack.
Start the backend with `make up` first, then:

```bash
cd web
npm install
npm run dev    # http://localhost:5173
```

Sign in with a demo user (`alice` or `bob`, password `password`).

## The documentation site

This site lives in the `docs/` directory:

```bash
cd docs
npm install
npm start      # dev server at http://localhost:3000
npm run build  # production build
```
