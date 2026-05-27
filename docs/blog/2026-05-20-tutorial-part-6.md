---
slug: tutorial-part-6
title: "Part 6: The React SPA"
authors: [asmith]
tags: [tutorial, series]
date: 2026-05-20T10:00
---

The first thing a user actually sees. By the end of Part 6 you'll have a
React SPA that signs in via Authorization Code + PKCE against the auth-server,
records the device the user is on, and renders a dashboard with their
profile and devices.

{/* truncate */}

## The whole journey

| # | What you'll do |
|---|---|
| 1 | Set up the monorepo |
| 2 | A Docusaurus docs site |
| 3 | auth-server — OAuth2 Authorization Server with a legacy client table |
| 4 | resource-api — the resource server |
| 5 | nginx reverse proxy |
| 6 | **The React SPA** *(you are here)* |
| 7 | Terraform infrastructure |
| 8 | GitHub Actions — CI and CD |
| 9 | AWS account setup |
| 10 | First deploy and teardown |

## Prerequisites

- All of Parts 1–5 done. Run `make up` and confirm
  `curl http://localhost:8088/.well-known/openid-configuration` returns JSON.
- Node 20+.

## What you'll build

- `web/` — a Vite + React 19 + TypeScript app.
- **Tailwind CSS v3** + **shadcn/ui**-style components (`Button`, `Card`,
  `Badge`) and the `cn` util.
- `react-oidc-context` doing the Authorization Code + PKCE dance.
- A login page that redirects to auth-server.
- A dashboard that:
  - Records the current device with `POST /api/devices`.
  - Renders the user profile via `GET /api/me`.
  - Lists devices via `GET /api/devices`.
- A built-in dev server on `http://localhost:5173`.

## Step 1 — Scaffold Vite

From the repo root:

```sh
npm create vite@latest web -- --template react-ts
cd web
```

If prompted, choose **React** + **TypeScript**. This creates `web/` with the
Vite + React 19 + TS template.

## Step 2 — Install dependencies

Runtime deps:

```sh
npm install \
  oidc-client-ts react-oidc-context \
  @radix-ui/react-slot \
  class-variance-authority clsx tailwind-merge \
  lucide-react
```

Dev deps (Tailwind v3 — current shadcn/ui works with v3 cleanly):

```sh
npm install -D \
  tailwindcss@3 postcss autoprefixer \
  tailwindcss-animate
```

Then run the base install:

```sh
npm install
```

## Step 3 — Tailwind, PostCSS, components.json

Create `web/tailwind.config.js`:

```js
import tailwindcssAnimate from 'tailwindcss-animate'

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      },
    },
  },
  plugins: [tailwindcssAnimate],
}
```

Create `web/postcss.config.js`:

```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

Create `web/components.json` (the shadcn/ui config):

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "tailwind.config.js",
    "css": "src/index.css",
    "baseColor": "slate",
    "cssVariables": true
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "iconLibrary": "lucide"
}
```

## Step 4 — Vite path alias + TS config

Replace `web/vite.config.ts`:

```ts
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, 'src'),
    },
  },
  server: {
    port: 5173,
  },
})
```

Replace `web/tsconfig.app.json`:

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "es2023",
    "lib": ["ES2023", "DOM"],
    "module": "esnext",
    "types": ["vite/client"],
    "skipLibCheck": true,

    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",

    "paths": {
      "@/*": ["./src/*"]
    },

    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "erasableSyntaxOnly": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"]
}
```

## Step 5 — Brand CSS

Replace `web/src/index.css`:

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    --background: 210 40% 98%;
    --foreground: 222 47% 11%;
    --card: 0 0% 100%;
    --card-foreground: 222 47% 11%;
    --primary: 243 75% 59%;
    --primary-foreground: 0 0% 100%;
    --secondary: 210 40% 94%;
    --secondary-foreground: 222 47% 11%;
    --muted: 210 40% 94%;
    --muted-foreground: 215 16% 47%;
    --accent: 210 40% 94%;
    --accent-foreground: 222 47% 11%;
    --destructive: 0 72% 51%;
    --destructive-foreground: 0 0% 100%;
    --border: 214 32% 89%;
    --input: 214 32% 89%;
    --ring: 243 75% 59%;
    --radius: 0.6rem;
  }

  .dark {
    --background: 222 47% 10%;
    --foreground: 210 40% 98%;
    --card: 222 47% 13%;
    --card-foreground: 210 40% 98%;
    --primary: 243 80% 74%;
    --primary-foreground: 222 47% 11%;
    --secondary: 217 33% 20%;
    --secondary-foreground: 210 40% 98%;
    --muted: 217 33% 20%;
    --muted-foreground: 215 20% 65%;
    --accent: 217 33% 20%;
    --accent-foreground: 210 40% 98%;
    --destructive: 0 63% 50%;
    --destructive-foreground: 210 40% 98%;
    --border: 217 33% 24%;
    --input: 217 33% 24%;
    --ring: 243 80% 74%;
  }
}

@layer base {
  * {
    @apply border-border;
  }

  body {
    @apply bg-background text-foreground;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  }
}
```

## Step 6 — The `cn` util and shadcn-style components

Create `web/src/lib/utils.ts`:

```ts
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Merges class names, resolving Tailwind conflicts. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

Create `web/src/components/ui/button.tsx`:

<details>
<summary>Button.tsx — shadcn/ui "new-york" style</summary>

```tsx
import type { ComponentProps } from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/lib/utils'

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground hover:bg-primary/90',
        destructive:
          'bg-destructive text-destructive-foreground hover:bg-destructive/90',
        outline:
          'border border-input bg-background hover:bg-accent hover:text-accent-foreground',
        secondary:
          'bg-secondary text-secondary-foreground hover:bg-secondary/80',
        ghost: 'hover:bg-accent hover:text-accent-foreground',
        link: 'text-primary underline-offset-4 hover:underline',
      },
      size: {
        default: 'h-9 px-4 py-2',
        sm: 'h-8 rounded-md px-3 text-xs',
        lg: 'h-11 rounded-md px-8 text-base',
        icon: 'h-9 w-9',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
)

type ButtonProps = ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }

function Button({ className, variant, size, asChild = false, ...props }: ButtonProps) {
  const Comp = asChild ? Slot : 'button'
  return (
    <Comp className={cn(buttonVariants({ variant, size, className }))} {...props} />
  )
}

export { Button, buttonVariants }
export type { ButtonProps }
```

</details>

Create `web/src/components/ui/card.tsx`:

```tsx
import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

function Card({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div className={cn('rounded-xl border bg-card text-card-foreground shadow-sm', className)} {...props} />
  )
}
function CardHeader({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex flex-col space-y-1.5 p-6', className)} {...props} />
}
function CardTitle({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('font-semibold leading-none tracking-tight', className)} {...props} />
}
function CardDescription({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('text-sm text-muted-foreground', className)} {...props} />
}
function CardContent({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('p-6 pt-0', className)} {...props} />
}
function CardFooter({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex items-center p-6 pt-0', className)} {...props} />
}

export { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter }
```

Create `web/src/components/ui/badge.tsx`:

```tsx
import type { ComponentProps } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold transition-colors',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        secondary: 'border-transparent bg-secondary text-secondary-foreground',
        outline: 'text-foreground',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

type BadgeProps = ComponentProps<'span'> & VariantProps<typeof badgeVariants>

function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}

export { Badge, badgeVariants }
```

## Step 7 — OIDC config

Create `web/src/auth.ts`:

```ts
import { WebStorageStateStore } from 'oidc-client-ts'
import type { AuthProviderProps } from 'react-oidc-context'

const origin = window.location.origin

/**
 * OIDC configuration for the Authorization Code + PKCE flow against the
 * auth-server (reached through the nginx reverse proxy).
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8088',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'spa-client',
  redirect_uri: `${origin}/callback`,
  post_logout_redirect_uri: origin,
  scope: 'openid profile read',
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  onSigninCallback: () => {
    // Drop the ?code=... query string after a successful sign-in.
    window.history.replaceState({}, document.title, '/')
  },
}
```

`react-oidc-context` does PKCE automatically for the Authorization Code flow.
The user store is `localStorage`, so refreshing the page keeps the user
signed in until the token expires.

Create `web/src/vite-env.d.ts`:

```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_OIDC_AUTHORITY?: string
  readonly VITE_OIDC_CLIENT_ID?: string
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
```

## Step 8 — API client + device detection

Create `web/src/lib/api.ts`:

```ts
const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8088'

export interface UserInfo {
  username: string
  fullName: string | null
  email: string | null
  phone: string | null
  department: string | null
  jobTitle: string | null
}

export interface DeviceInfo {
  deviceName: string
  deviceType: string | null
  os: string | null
  browser: string | null
  lastSeenAt: string | null
  registeredAt: string | null
}

export interface RegisterDeviceRequest {
  deviceName: string
  deviceType: string
  os: string
  browser: string
}

async function request<T>(path: string, token: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) {
    throw new Error(`Request to ${path} failed (${response.status})`)
  }
  return response.json() as Promise<T>
}

/** Thin client for the resource-api endpoints. */
export const api = {
  getMe: (token: string) => request<UserInfo>('/api/me', token),
  getDevices: (token: string) => request<DeviceInfo[]>('/api/devices', token),
  registerDevice: (token: string, body: RegisterDeviceRequest) =>
    request<DeviceInfo>('/api/devices', token, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
}
```

Create `web/src/lib/device.ts`:

```ts
import type { RegisterDeviceRequest } from './api'

/** Builds a best-effort description of the current device from the user agent. */
export function detectDevice(): RegisterDeviceRequest {
  const ua = navigator.userAgent
  const browser = detectBrowser(ua)
  const os = detectOs(ua)
  const deviceType = detectDeviceType(ua)
  return { deviceName: `${browser} on ${os}`, deviceType, os, browser }
}

function detectBrowser(ua: string): string {
  if (/Edg\//.test(ua)) return 'Edge'
  if (/Firefox\//.test(ua)) return 'Firefox'
  if (/Chrome\//.test(ua)) return 'Chrome'
  if (/Safari\//.test(ua)) return 'Safari'
  return 'Unknown browser'
}

function detectOs(ua: string): string {
  if (/Windows/.test(ua)) return 'Windows'
  if (/Mac OS X/.test(ua)) return 'macOS'
  if (/Android/.test(ua)) return 'Android'
  if (/iPhone|iPad/.test(ua)) return 'iOS'
  if (/Linux/.test(ua)) return 'Linux'
  return 'Unknown OS'
}

function detectDeviceType(ua: string): string {
  if (/iPad|Tablet/i.test(ua)) return 'tablet'
  if (/Mobi|Android|iPhone/i.test(ua)) return 'phone'
  return 'laptop'
}
```

## Step 9 — Logo + Header

Create `web/src/components/GatewayLogo.tsx`:

```tsx
export function GatewayLogo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" fill="none" className={className} aria-hidden="true">
      <path d="M7 35 V18 a13 13 0 0 1 26 0 V35" stroke="currentColor" strokeWidth="5" strokeLinecap="round" />
      <path d="M15 35 V19 a5 5 0 0 1 10 0 V35" stroke="currentColor" strokeOpacity="0.5" strokeWidth="3" strokeLinecap="round" />
      <circle cx="20" cy="27" r="3.6" fill="#06b6d4" />
    </svg>
  )
}
```

Create `web/src/components/Header.tsx`:

```tsx
import { LogOut } from 'lucide-react'
import { useAuth } from 'react-oidc-context'

import { GatewayLogo } from '@/components/GatewayLogo'
import { Button } from '@/components/ui/button'

export function Header() {
  const auth = useAuth()
  return (
    <header className="border-b bg-card">
      <div className="mx-auto flex max-w-4xl items-center justify-between p-4">
        <div className="flex items-center gap-2 text-primary">
          <GatewayLogo className="h-7 w-7" />
          <span className="font-semibold text-foreground">API Gateway Pilot</span>
        </div>
        <Button variant="outline" size="sm" onClick={() => void auth.removeUser()}>
          <LogOut />
          Sign out
        </Button>
      </div>
    </header>
  )
}
```

## Step 10 — Pages

Create `web/src/pages/LoginPage.tsx`:

```tsx
import { LogIn } from 'lucide-react'
import { useAuth } from 'react-oidc-context'

import { GatewayLogo } from '@/components/GatewayLogo'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

export function LoginPage() {
  const auth = useAuth()
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-indigo-600 to-indigo-900 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="items-center text-center">
          <div className="mb-2 flex h-14 w-14 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <GatewayLogo className="h-8 w-8" />
          </div>
          <CardTitle className="text-2xl">API Gateway Pilot</CardTitle>
          <CardDescription>Sign in to view your profile and devices.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Button size="lg" className="w-full" onClick={() => void auth.signinRedirect()}>
            <LogIn />
            Sign in
          </Button>
          <p className="text-center text-xs text-muted-foreground">
            Demo users <code className="font-mono">alice</code> /{' '}
            <code className="font-mono">bob</code> — password{' '}
            <code className="font-mono">password</code>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
```

Create `web/src/pages/DashboardPage.tsx`:

<details>
<summary>DashboardPage.tsx — registers the device, loads /me + /devices, renders profile and device cards</summary>

```tsx
import { useEffect, useState } from 'react'
import { Laptop, Monitor, Smartphone, Tablet } from 'lucide-react'
import { useAuth } from 'react-oidc-context'

import { Header } from '@/components/Header'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { api } from '@/lib/api'
import type { DeviceInfo, UserInfo } from '@/lib/api'
import { detectDevice } from '@/lib/device'

export function DashboardPage() {
  const auth = useAuth()
  const token = auth.user?.access_token ?? ''
  const [user, setUser] = useState<UserInfo | null>(null)
  const [devices, setDevices] = useState<DeviceInfo[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    setLoading(true)
    void (async () => {
      try {
        await api.registerDevice(token, detectDevice())
        const [me, deviceList] = await Promise.all([
          api.getMe(token),
          api.getDevices(token),
        ])
        if (!cancelled) {
          setUser(me)
          setDevices(deviceList)
          setError(null)
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load your data')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [token])

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="mx-auto max-w-4xl space-y-6 p-6">
        <div>
          <h1 className="text-2xl font-bold">Dashboard</h1>
          <p className="text-sm text-muted-foreground">
            Your profile and the devices you have signed in from.
          </p>
        </div>

        {loading && <p className="text-muted-foreground">Loading…</p>}

        {error && (
          <Card className="border-destructive">
            <CardContent className="pt-6 text-sm text-destructive">{error}</CardContent>
          </Card>
        )}

        {user && <ProfileCard user={user} />}
        {!loading && !error && <DeviceList devices={devices} />}
      </main>
    </div>
  )
}

function ProfileCard({ user }: { user: UserInfo }) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-3">
          <CardTitle className="text-lg">{user.fullName ?? user.username}</CardTitle>
          <Badge variant="secondary">{user.username}</Badge>
        </div>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2">
        <Field label="Email" value={user.email} />
        <Field label="Phone" value={user.phone} />
        <Field label="Department" value={user.department} />
        <Field label="Job title" value={user.jobTitle} />
      </CardContent>
    </Card>
  )
}

function Field({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <p className="text-sm">{value ?? '—'}</p>
    </div>
  )
}

function DeviceList({ devices }: { devices: DeviceInfo[] }) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">Devices</h2>
      {devices.length === 0 ? (
        <p className="text-sm text-muted-foreground">No devices recorded yet.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {devices.map((device) => (
            <DeviceCard key={device.deviceName} device={device} />
          ))}
        </div>
      )}
    </section>
  )
}

function DeviceCard({ device }: { device: DeviceInfo }) {
  return (
    <Card>
      <CardContent className="flex gap-3 pt-6">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-secondary text-primary">
          <DeviceIcon type={device.deviceType} />
        </div>
        <div className="min-w-0">
          <p className="truncate font-medium">{device.deviceName}</p>
          <p className="text-sm text-muted-foreground">
            {[device.os, device.browser].filter(Boolean).join(' · ') || '—'}
          </p>
          {device.lastSeenAt && (
            <p className="mt-1 text-xs text-muted-foreground">
              Last seen {new Date(device.lastSeenAt).toLocaleString()}
            </p>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function DeviceIcon({ type }: { type: string | null }) {
  switch (type) {
    case 'phone': return <Smartphone className="h-5 w-5" />
    case 'tablet': return <Tablet className="h-5 w-5" />
    case 'laptop': return <Laptop className="h-5 w-5" />
    default: return <Monitor className="h-5 w-5" />
  }
}
```

</details>

The dashboard does three things on mount: POSTs a device record (the user's
current browser), then GETs `/api/me` and `/api/devices` in parallel.

## Step 11 — App.tsx, main.tsx, index.html

Replace `web/src/App.tsx`:

```tsx
import { useAuth } from 'react-oidc-context'

import { DashboardPage } from '@/pages/DashboardPage'
import { LoginPage } from '@/pages/LoginPage'

export default function App() {
  const auth = useAuth()

  if (auth.isLoading) return <CenteredMessage text="Loading…" />
  if (auth.error) return <CenteredMessage text={`Authentication error: ${auth.error.message}`} />
  return auth.isAuthenticated ? <DashboardPage /> : <LoginPage />
}

function CenteredMessage({ text }: { text: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background text-muted-foreground">
      {text}
    </div>
  )
}
```

No router. The SPA has exactly two states — signed in or not — so a single
conditional render is plenty. When the OAuth redirect comes back to
`/callback?code=...`, `react-oidc-context` processes the code; the
`onSigninCallback` in `auth.ts` then rewrites the URL to `/`.

Replace `web/src/main.tsx`:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'

import App from './App.tsx'
import { oidcConfig } from './auth.ts'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...oidcConfig}>
      <App />
    </AuthProvider>
  </StrictMode>,
)
```

Replace `web/index.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>API Gateway Pilot</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

Create `web/public/favicon.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
  <rect width="32" height="32" rx="7" fill="#4f46e5"/>
  <path d="M8 25 V15 a8 8 0 0 1 16 0 V25" stroke="#ffffff" stroke-width="3.5" fill="none" stroke-linecap="round"/>
  <circle cx="16" cy="20" r="2.7" fill="#06b6d4"/>
</svg>
```

## Step 12 — `.env.example`

Create `web/.env.example`:

```dotenv
# Copy to .env.local to override. Defaults target the local docker-compose stack.

# OIDC authority — the nginx-fronted auth-server.
VITE_OIDC_AUTHORITY=http://localhost:8088
VITE_OIDC_CLIENT_ID=spa-client

# Base URL for resource-api calls — the nginx-fronted backend.
VITE_API_BASE=http://localhost:8088
```

## Step 13 — Clean up Vite scaffold leftovers

```sh
rm -rf src/assets src/App.css
# The scaffold's favicon was already replaced in step 11.
```

## Verify

Make sure the backend is up:

```sh
make up   # from the repo root
make ps   # all four containers (healthy)
```

Then in `web/`:

```sh
npm run dev
```

You should see `Local: http://localhost:5173/`. Open it. You'll see the
**API Gateway Pilot** card on an indigo gradient with a **Sign in** button.
Click it. You get redirected to **http://localhost:8088/login** (the
nginx-fronted login page from Part 3). Sign in as **`alice`** /
**`password`**. After redirect-back, you'll land on the dashboard showing:

- A profile card with Alice's seeded info (Email, Phone, Department, Job title).
- A **Devices** section with three cards — the two seeded devices from Part 4
  plus a third one for the browser you just signed in from (the
  `POST /api/devices` call on mount).

Sign out, sign in as **`bob`**, and you'll see Bob's one device.

## Build for production

```sh
npm run build
```

`tsc -b && vite build` runs the type-checker and then bundles into `dist/`.
Clean output means TypeScript is happy.

## Commit

```sh
cd ..
git add -A
git commit -m "feat: react spa with pkce login and dashboard"
```

## What's next

**Part 7 — Terraform infrastructure.** The first stretch into AWS land. By
the end of Part 7 you'll have Terraform that can stand up the full prototype
in your account — VPC, ECS Fargate + Service Connect, ALB, RDS, ECR, S3 +
CloudFront, optional WAF — all destroyable with one `terraform destroy`.
