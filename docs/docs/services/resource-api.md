---
sidebar_position: 2
title: resource-api
---

# resource-api

The resource server. Spring Boot 3.5, JDK 21. It serves the user and device
information shown on the dashboard.

## What it does

- Validates JWT access tokens against the auth-server's published JWK set.
- Serves the current user's profile and devices.
- Records the device a user signed in from.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/me` | The current user's profile |
| GET | `/api/devices` | The current user's devices |
| POST | `/api/devices` | Record the device the user signed in from |
| GET | `/actuator/health` | Health check |

All `/api/**` endpoints require a valid JWT access token.

## How tokens are validated

resource-api never sees a password. It validates each request's bearer token
against the auth-server's JWKS endpoint — it fetches the public signing keys and
checks the token's signature and expiry locally. No call back to the auth-server
is needed per request.

## Data

resource-api owns the `app` schema:

- `user_info` — profile fields, keyed by username.
- `device_info` — devices, linked to a user.

The two services share one database but separate schemas, and correlate on
`username` (the JWT subject) rather than a cross-schema foreign key — so each
service can be deployed on its own.

## Running it

Locally it runs as part of the Docker Compose stack — see
[Local development](../local-development.md). It listens on port `8080`.
