---
slug: three-rough-edges
title: Three rough edges from the first `make up`
authors: [asmith]
tags: [milestone]
date: 2026-05-22T14:00
---

We brought the prototype up on a laptop today, and it worked — but not on the
first try. Three small things needed fixing first. Each is the kind of gotcha
that's obvious in retrospect and easy to lose an hour to, so worth writing
down.

{/* truncate */}

## Docker Desktop's first launch sets up `docker compose`

Installing Docker Desktop via Homebrew (`brew install --cask docker`) is enough
to put the `docker` CLI on `PATH`, but the **compose plugin** doesn't get wired
up until you actually launch the app at least once. The symptom is unhelpful
in hindsight:

```
$ docker compose up -d
unknown shorthand flag: 'd' in -d
```

Docker is interpreting `compose up -d` as raw arguments because `compose`
isn't a known subcommand. Launching Docker Desktop once (`open -a Docker`)
lets it install its symlinks, and `docker compose` starts working.

## Multi-module Maven needs every child POM in the build context

The two Spring services share a parent POM. Each service's Dockerfile only
copied its own POM into the build context:

```dockerfile
COPY pom.xml ./
COPY auth-server/pom.xml auth-server/pom.xml
COPY auth-server/src auth-server/src
RUN mvn -pl auth-server -am -DskipTests clean package
```

Maven reads the parent POM, sees `<modules>resource-api</modules>`, and fails
before it even starts building:

```
Child module /workspace/resource-api of /workspace/pom.xml does not exist
```

`-pl auth-server -am` controls what Maven *builds* — it does not change what
Maven *validates*. Every module listed in the parent has to be readable, or
the reactor refuses to start. The fix is to copy every module's `pom.xml` (just
the POM, not the sources), and let Maven only build the one whose source you
provided.

## `$host` in nginx strips the port

After login, the browser landed on `http://localhost/login` — port 80, nothing
listening — with `ERR_CONNECTION_REFUSED`. The nginx config used:

```nginx
proxy_set_header Host             $host;
proxy_set_header X-Forwarded-Host $host;
```

`$host` in nginx is just the hostname; the port is stripped. Spring is
configured with `forward-headers-strategy: framework`, so it rebuilds request
URLs from `X-Forwarded-Host` + `X-Forwarded-Proto`. With no port in either
forwarded header, it defaulted to `80` for `http`, and the redirect went to
`http://localhost/login`.

The fix is `$http_host` (the original `Host` header verbatim, port included)
and an explicit `X-Forwarded-Port`:

```nginx
proxy_set_header Host             $http_host;
proxy_set_header X-Forwarded-Host $http_host;
proxy_set_header X-Forwarded-Port $server_port;
```

Three lines, but it's the kind of detail a reverse proxy in front of a Spring
app needs right or absolutely nothing about OAuth redirects will work.

After that the prototype runs cleanly end to end — sign in, dashboard,
devices, the works. AWS next.
