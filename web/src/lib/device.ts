import type { RegisterDeviceRequest } from './api'

/** Builds a best-effort description of the current device from the user agent. */
export function detectDevice(): RegisterDeviceRequest {
  const ua = navigator.userAgent
  const browser = detectBrowser(ua)
  const os = detectOs(ua)
  const deviceType = detectDeviceType(ua)
  return {
    deviceName: `${browser} on ${os}`,
    deviceType,
    os,
    browser,
  }
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
