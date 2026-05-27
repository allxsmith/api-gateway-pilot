---
slug: tutorial-part-1
title: "Part 1: Setting up the monorepo"
authors: [asmith]
tags: [tutorial, series]
date: 2026-05-12T10:00
---

This is the first post in a ten-part walk-through that builds the API Gateway
Pilot from an empty directory. By the end of Part 1 you'll have a monorepo
skeleton on your machine with a PostgreSQL database running in Docker — the
foundation everything else sits on.

{/* truncate */}

## The whole journey

| # | What you'll do |
|---|---|
| 1 | **Set up the monorepo** *(you are here)* |
| 2 | A Docusaurus docs site |
| 3 | auth-server — OAuth2 Authorization Server with a legacy client table |
| 4 | resource-api — the resource server |
| 5 | nginx reverse proxy |
| 6 | The React SPA |
| 7 | Terraform infrastructure |
| 8 | GitHub Actions — CI and CD |
| 9 | AWS account setup |
| 10 | First deploy and teardown |

Parts 1–6 stay on your laptop. Parts 7–10 take it to AWS, on a personal account
you control.

## Prerequisites

Install these once. The versions in brackets are what the rest of the series
is built and tested against:

- **JDK 21** (Temurin 21.0.x) — `brew install --cask temurin@21`
- **Maven 3.9+** — `brew install maven`
- **Node.js 20+** (Node 22 LTS recommended) — `brew install node@22`
- **Docker Desktop** — `brew install --cask docker` *then launch it once* so
  the `docker compose` plugin gets wired up.
- A **GitHub** account and **Git** configured locally (`git config --global
  user.name` / `user.email` set).

Nothing about AWS yet — Parts 1–6 don't need an AWS account.

## What you'll build in Part 1

- A new directory `api-gateway-pilot/` with a fresh git repo.
- A parent Maven POM that pins JDK 21 and Spring Boot 3.5.
- Standard repo files: `.gitignore`, `.gitattributes`, `.editorconfig`.
- A `Makefile` of convenience targets (`make up`, `make ps`, `make down`, …).
- A `.env.example` of safe local defaults.
- A `docker-compose.yml` that starts PostgreSQL.

At the end you'll run `make up` and see Postgres come up healthy.

## Step 1 — Create the project and the git repo

```sh
mkdir api-gateway-pilot
cd api-gateway-pilot
git init -b main
```

`git init -b main` initialises with `main` as the default branch (avoids
needing to rename later).

## Step 2 — Drop in the repo metadata files

Three small files that govern formatting, line endings, and what gets
committed.

**`.gitignore`** — what NOT to commit:

```gitignore
# --- Java / Maven ---
target/
*.class
!.mvn/wrapper/maven-wrapper.jar
.mvn/timing.properties

# --- Node ---
node_modules/
npm-debug.log*
yarn-debug.log*
yarn-error.log*
.pnpm-debug.log*

# --- Docusaurus ---
docs/build/
docs/.docusaurus/
docs/.cache-loader/
docs/docs/api/

# --- Vite / web build ---
web/dist/
web/dist-ssr/

# --- Terraform ---
**/.terraform/*
*.tfstate
*.tfstate.*
crash.log
crash.*.log
*.tfvars.local
override.tf
override.tf.json
*_override.tf
*_override.tf.json
.terraformrc
terraform.rc
backend.hcl

# --- Env / secrets ---
.env
.env.local
*.local

# --- IDE / editors ---
.idea/
*.iml
*.ipr
*.iws
.vscode/
*.swp

# --- OS ---
.DS_Store
Thumbs.db

# --- Logs ---
*.log
logs/
```

It's longer than this part needs, but it covers everything the next nine
parts add (Docusaurus, Vite, Terraform, etc.) so you won't have to revisit it.

**`.gitattributes`** — line-ending normalization:

```gitattributes
# Normalize line endings
* text=auto eol=lf

# Scripts and wrappers stay LF
*.sh text eol=lf
mvnw text eol=lf

# Windows scripts stay CRLF
*.bat text eol=crlf
*.cmd text eol=crlf

# Binary assets
*.jar binary
*.png binary
*.jpg binary
*.jpeg binary
*.gif binary
*.ico binary
*.woff binary
*.woff2 binary
```

**`.editorconfig`** — consistent indentation across editors:

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 2

[*.{java,xml}]
indent_size = 4

[*.md]
trim_trailing_whitespace = false

[Makefile]
indent_style = tab
```

The Makefile section is important — Make syntax requires tabs, not spaces, for
recipe lines.

## Step 3 — The parent Maven POM

Spring Boot 3.5 is the version line we target. JDK 21 is the language level.
Create `pom.xml` at the repo root:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Inherit Spring Boot dependency + plugin management. -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.13</version>
        <relativePath/>
    </parent>

    <groupId>com.apipilot</groupId>
    <artifactId>api-gateway-pilot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>api-gateway-pilot</name>
    <description>Prototype API gateway architecture on AWS</description>

    <!--
      Thin aggregator POM. Centralizes the JDK level and shared dependency
      versions for the two Spring Boot services. Each service still builds
      independently (cd auth-server && mvn package).
    -->
    <properties>
        <java.version>21</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <springdoc.version>2.8.6</springdoc.version>
    </properties>

    <modules>
        <!-- auth-server is added in Part 3; resource-api in Part 4. -->
    </modules>

</project>
```

Two things to flag:

- `<packaging>pom</packaging>` — this is an aggregator POM, not something that
  builds itself into a jar. It points Maven at the modules and centralises
  versions.
- `<modules>` is empty for now. Part 3 adds `auth-server`; Part 4 adds
  `resource-api`. Leaving it empty means `mvn validate` succeeds on what we
  have so far.

Verify the POM is well-formed:

```sh
mvn -q validate
```

It should exit silently (no output, exit code 0). If you see "java.version
21 not supported," check `java -version` and `mvn -v` both report JDK 21.

## Step 4 — The Makefile

A handful of `make` targets save typing as the stack grows. Create `Makefile`
at the repo root — **the recipe lines must start with a tab character**, not
spaces:

```make
# api-gateway-pilot - local development convenience targets
.DEFAULT_GOAL := help
COMPOSE := docker compose

.PHONY: help up down logs ps db clean build test

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

up: ## Start the local stack (docker-compose)
	$(COMPOSE) up -d

down: ## Stop the local stack
	$(COMPOSE) down

logs: ## Tail local stack logs
	$(COMPOSE) logs -f

ps: ## Show local stack status
	$(COMPOSE) ps

db: ## Open a psql shell on the local database
	$(COMPOSE) exec postgres psql -U apipilot -d apipilot

clean: ## Stop the local stack and remove volumes
	$(COMPOSE) down -v

build: ## Build the Java services
	mvn -q -T1C package

test: ## Run Java tests
	mvn -q test
```

`make help` (or just `make`) lists the targets. The two-spaces-or-tabs trap
catches everyone once — if `make up` reports `*** missing separator`, your
editor saved spaces instead of tabs.

## Step 5 — Local env defaults

A `.env.example` file documents the local environment variables. Each later
part adds to it; for Part 1 it's just the database knobs.

Create `.env.example`:

```dotenv
# Copy to .env and adjust as needed. .env is gitignored.
# Values here are safe local-only defaults for the prototype.

# --- Local Postgres (docker-compose) ---
POSTGRES_DB=apipilot
POSTGRES_USER=apipilot
POSTGRES_PASSWORD=apipilot
POSTGRES_PORT=5432

# --- Database connection used by the Spring services ---
DB_HOST=localhost
DB_PORT=5432
DB_NAME=apipilot
DB_USERNAME=apipilot
DB_PASSWORD=apipilot

# --- Service ports ---
AUTH_SERVER_PORT=9000
RESOURCE_API_PORT=8080
NGINX_PORT=8088

# --- OAuth / JWT (wired up from Part 3 onward) ---
# Issuer the tokens are minted under; resource-api validates against it.
AUTH_ISSUER_URI=http://localhost:8088
# JWK Set URI resource-api uses to fetch token-signing keys.
JWK_SET_URI=http://localhost:8088/oauth2/jwks
```

Now copy it to `.env`:

```sh
cp .env.example .env
```

`.env` is in `.gitignore`, so your local values stay local.

## Step 6 — docker-compose with Postgres

Create `docker-compose.yml`:

```yaml
# Local development stack.
# auth-server, resource-api, and nginx are added in later parts.
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

volumes:
  pgdata:
```

A few choices worth knowing:

- `postgres:16-alpine` — a small, current image. PostgreSQL 16 matches what
  RDS Postgres uses in Part 7.
- The `${VAR:-default}` syntax lets `.env` override values without making the
  file mandatory.
- `volumes: pgdata` is a named Docker volume — your data survives
  `make down`. `make clean` removes the volume for a fresh start.
- The healthcheck uses `pg_isready` so other services (added in Parts 3–5)
  can wait on `service_healthy`.

## Step 7 — A README stub

A short README so the repo isn't a mystery on GitHub. Create `README.md`:

```markdown
# API Gateway Pilot

A prototype API gateway architecture on AWS, designed to be torn down and
recreated freely while planning a real migration toward Amazon API Gateway
and Cognito.

This repository is built up across a ten-part tutorial series — see the
docs site for the walk-through.

## Local development

```sh
cp .env.example .env
make up          # start the local stack
make ps          # check status
make down        # stop
```

`make help` lists every target.
```

You'll flesh this out in later parts.

## Verify

Bring up Postgres and confirm it's healthy:

```sh
make up
make ps
```

After ~5 seconds you should see something like:

```
NAME           IMAGE                COMMAND                  ...  STATUS
agp-postgres   postgres:16-alpine   "docker-entrypoint.s…"   ...  Up 5s (healthy)
```

Open a psql shell:

```sh
make db
```

You should land at `apipilot=#`. `\q` to exit.

Stop the stack:

```sh
make down
```

## Commit

First commit:

```sh
git add -A
git commit -m "chore: initialize monorepo structure"
```

You now have an empty-but-real monorepo. Pushing to GitHub is optional at this
stage — wait for Part 8 if you want to set up CI to run on each push.

## What's next

**Part 2 — A Docusaurus docs site.** Scaffold the documentation site where
these very posts live, with Mermaid diagrams, an OpenAPI plugin, and a custom
brand.
