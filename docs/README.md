# API Gateway Pilot — documentation site

The documentation site for the [API Gateway Pilot](../README.md) prototype,
built with [Docusaurus](https://docusaurus.io/).

## Develop

```bash
npm install
npm start        # dev server at http://localhost:3000
```

## Build

```bash
npm run build    # static output in build/
npm run serve    # preview the production build
```

## What's here

- `docs/` — documentation pages (architecture, setup guides)
- `blog/` — engineering build notes
- `openapi/` — OpenAPI specs; generated into `docs/api/` on every build by
  `docusaurus-plugin-openapi-docs` (run `npm run gen-api-docs` to regenerate
  by hand)
- `src/` — homepage, theme, and components
- `static/img/` — SVG imagery and diagrams

Deployment to GitHub Pages is handled by a GitHub Actions workflow.
