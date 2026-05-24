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
