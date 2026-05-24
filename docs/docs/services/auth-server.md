---
sidebar_position: 1
title: auth-server
---

# auth-server

The OAuth2 Authorization Server. Spring Boot 3.5, JDK 21, built on Spring
Authorization Server.

## What it does

- Issues JWT access and refresh tokens.
- Hosts a form login page.
- Supports the **Authorization Code + PKCE** flow for the React SPA.
- Supports the **client credentials** flow for service-to-service calls.
- Publishes its signing keys at the JWKS endpoint so `resource-api` can
  validate tokens without calling back.

## The legacy client table

Many older Spring projects store OAuth clients in `oauth_client_details` — the
schema from the long-deprecated Spring Security OAuth2 project. Modern Spring
Authorization Server uses a different table (`oauth2_registered_client`) with a
different shape.

The prototype demonstrates reading the legacy table directly with a small
adapter, `LegacyClientMapper`, that translates each row into the
`RegisteredClient` the modern server expects:

- a client with **no secret** becomes a public client — client authentication
  `NONE`, PKCE required;
- a client **with a secret** stays confidential (`CLIENT_SECRET_BASIC`);
- `autoapprove = 'true'` skips the consent screen;
- legacy grant types the modern server dropped (`password`, `implicit`) are
  ignored.

Authorization state (auth codes, consent) is kept in memory — the prototype
runs a single instance and access tokens are stateless JWTs.

## Key endpoints

| Endpoint | Purpose |
| --- | --- |
| `/oauth2/authorize` | Authorization endpoint (Authorization Code flow) |
| `/oauth2/token` | Token endpoint |
| `/oauth2/jwks` | JSON Web Key Set — public signing keys |
| `/.well-known/openid-configuration` | OIDC discovery document |
| `/login` | Form login page |
| `/actuator/health` | Health check |
| `/swagger-ui.html` | API documentation |

## Seeded data

On first start, auth-server seeds demo data if the tables are empty:

| Kind | Value |
| --- | --- |
| Demo users | `alice`, `bob` — password `password` |
| SPA client | `spa-client` — public, Authorization Code + PKCE |
| Service client | `resource-api` — confidential, client credentials |

## Running it

Locally it runs as part of the Docker Compose stack — see
[Local development](../local-development.md). It listens on port `9000`.
