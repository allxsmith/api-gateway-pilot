# web — API Gateway Pilot SPA

The React single-page app: a login screen and a dashboard showing the signed-in
user's profile and devices.

- **Vite** + **React** + **TypeScript**
- **Tailwind CSS** + **shadcn/ui** components
- **react-oidc-context** for the Authorization Code + PKCE login flow

## Develop

```bash
npm install
cp .env.example .env.local   # optional — defaults target the local stack
npm run dev                  # http://localhost:5173
```

The backend (auth-server + resource-api behind nginx) must be running — start it
from the repository root with `make up`.

## Build

```bash
npm run build                # type-checks and bundles into dist/
```
