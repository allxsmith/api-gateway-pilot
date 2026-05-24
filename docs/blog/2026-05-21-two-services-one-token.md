---
slug: two-services-one-token
title: Two services, one token
authors: [asmith]
tags: [milestone]
date: 2026-05-21T17:00
---

The resource API is up. It's the service the dashboard actually calls — it
serves a user's profile and the list of devices they've signed in from.

The interesting part is what it *doesn't* do.

{/* truncate */}

resource-api never sees a password and never holds a session. Every request
carries a JWT access token from the auth server. resource-api validates that
token by checking its signature against the auth server's public keys, which it
fetches from the JWKS endpoint. After that, validation is local — no call back
to the auth server per request.

That's the whole point of splitting them. The auth server owns identity. The
resource API owns data. They share a database — separate schemas — and a token
format, and nothing else. Either one can be deployed without the other.

There's no mesh layer in front of the services either. They find each other by
name, and a signed token is enough to trust a request. In AWS, ECS Service
Connect handles the name resolution — the trust model does not change.

Next: a reverse proxy so both services sit behind one URL, then the React app.
