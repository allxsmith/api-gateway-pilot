---
slug: replacing-legacy-spring-oauth
title: Replacing legacy Spring Security OAuth
authors: [asmith]
tags: [architecture]
date: 2026-05-22T10:00
---

The `spring-security-oauth2` project — the original Spring OAuth — has been
deprecated for years and is gone from current Spring Boot. Moving any project
still on it to Spring Boot 3.5 means moving to Spring Authorization Server.
This is what that involves in the prototype.

{/* truncate */}

## The client table

`spring-security-oauth2` stored OAuth clients in a table called
`oauth_client_details`. Spring Authorization Server uses a different table,
`oauth2_registered_client`, with a different shape — secrets, settings, and
token config are laid out differently, and there are JSON columns.

Migrating that data or running both schemas in parallel is annoying. The
prototype demonstrates a different option: read the legacy table directly with
a custom `RegisteredClientRepository`. It is one query and a small mapper:

- comma-separated `authorized_grant_types` becomes a set of
  `AuthorizationGrantType`;
- a row with no `client_secret` becomes a public client, PKCE required;
- a row with a secret stays confidential (`client_secret_basic`);
- `autoapprove = 'true'` skips the consent screen;
- grant types the new server dropped (`password`, `implicit`) are ignored.

About forty lines. The legacy table keeps working untouched.

## Tokens

The old server could issue opaque tokens checked against a database. The new
one issues JWTs by default. That is a better fit here: the resource server
validates them with public keys and never calls back. The legacy token tables
aren't needed.

## Flows

The legacy `password` grant — username and password posted straight to the
token endpoint — is gone, and good riddance. The SPA uses Authorization Code
with PKCE instead.

## Authorization state

Spring Authorization Server can store authorization and consent in the
database. The prototype keeps them in memory — one instance, stateless JWTs,
nothing to gain from a table.

## The takeaway

If you have a project still on `spring-security-oauth2`, you don't have to
migrate the client data to move to Spring Authorization Server. A small
adapter over the legacy table is enough, and it makes the cutover almost
boring.
