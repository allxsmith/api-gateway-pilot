---
sidebar_position: 4
title: web (React SPA)
---

# web — the React SPA

The single-page app: a login screen and a dashboard. Vite + React +
TypeScript, styled with Tailwind CSS and shadcn/ui components.

## What it does

- Signs the user in with the **Authorization Code + PKCE** flow against the
  auth-server (via `react-oidc-context`).
- Records the device the user signed in from (`POST /api/devices`).
- Shows the user's profile and devices, read from the resource-api.

## The login flow

1. The user clicks **Sign in**. The SPA redirects the browser to the
   auth-server's `/oauth2/authorize` endpoint.
2. The user enters credentials on the auth-server's login page.
3. The auth-server redirects back to the SPA with an authorization code.
4. The SPA exchanges the code for JWT access and refresh tokens — PKCE proves
   the SPA started the flow, so no client secret is needed.
5. Every API call carries the access token as a bearer token.

## One origin, no CORS surprises

Locally the SPA runs on the Vite dev server (`http://localhost:5173`) and talks
to the backend through nginx (`http://localhost:8088`). In AWS, CloudFront
serves the SPA and forwards `/api` and `/oauth2` to the ALB, so the SPA and the
backend share a single origin.

## Running it

See [Local development](../local-development.md). The backend (`make up`) must
be running first.
