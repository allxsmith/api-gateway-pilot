---
slug: standing-up-the-auth-server
title: Standing up the auth server
authors: [asmith]
tags: [milestone]
date: 2026-05-21T15:00
---

The auth server is running. It's Spring Boot 3.5 with Spring Authorization
Server — it issues JWT access and refresh tokens, has a login page, and
supports the Authorization Code + PKCE flow the React app will use.

The one wrinkle worth writing down is the client registry.

{/* truncate */}

Many older Spring projects store OAuth clients in a table called
`oauth_client_details` — the schema from the long-deprecated Spring Security
OAuth2 project. Modern Spring Authorization Server uses a different table, with
a different shape.

The prototype demonstrates reading the legacy table directly rather than
migrating the data. A small adapter, `LegacyClientMapper`, turns an
`oauth_client_details` row into the `RegisteredClient` the new server expects.
A client with no secret becomes a public client with PKCE required; one with a
secret stays confidential. Legacy grant types the new server dropped, like
`password`, are just ignored.

It's about forty lines of code, and any project that still has the legacy
table can keep using it untouched while moving to the modern server.

Authorization state — auth codes, consent — is kept in memory. The prototype
runs one instance and the access tokens are stateless JWTs, so there is nothing
to gain from a database table there.

Next: the resource API that actually serves the user and device data.
