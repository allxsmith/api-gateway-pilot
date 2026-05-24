---
slug: a-real-login-screen
title: A real login screen, end to end
authors: [asmith]
tags: [milestone]
date: 2026-05-21T19:00
---

There's a UI now. A React app with a login screen and a dashboard that shows
who you are and which devices you've signed in from. With it, the prototype
works end to end for the first time.

{/* truncate */}

The login is the part worth describing. The React app is a public client — it
runs in a browser, so it can't keep a secret. It uses the Authorization Code
flow with PKCE: the app generates a one-time code verifier, sends its hash to
the auth server, and proves it later when exchanging the authorization code for
tokens. No client secret anywhere.

The flow itself: click **Sign in**, get redirected to the auth server's login
page, sign in, land back on the app with tokens in hand. From then on every
request to the resource API carries the access token. The app never sees a
password.

The stack is Vite, React, TypeScript, and shadcn/ui for the components — quick
to build and easy to read. `react-oidc-context` handles the OAuth dance.

One nice side effect of the nginx proxy from last time: the app talks to a
single backend origin for both auth and data. No tangle of URLs.

The whole thing runs on a laptop now — Postgres, two services, a proxy, and a
front end. Next it needs somewhere to live: Terraform and AWS.
