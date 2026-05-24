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
    if (!token) {
      return
    }
    let cancelled = false
    setLoading(true)
    void (async () => {
      try {
        // Record the device the user signed in from, then load the dashboard.
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
        if (!cancelled) {
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
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
            <CardContent className="pt-6 text-sm text-destructive">
              {error}
            </CardContent>
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
          <CardTitle className="text-lg">
            {user.fullName ?? user.username}
          </CardTitle>
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
    case 'phone':
      return <Smartphone className="h-5 w-5" />
    case 'tablet':
      return <Tablet className="h-5 w-5" />
    case 'laptop':
      return <Laptop className="h-5 w-5" />
    default:
      return <Monitor className="h-5 w-5" />
  }
}
