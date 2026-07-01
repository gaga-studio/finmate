import type { UserMeResponse } from './types'

export type FinMateSession = {
  accessToken?: string
  expiresAt?: string
  user?: UserMeResponse
  canRefresh?: boolean
}

const SESSION_KEY = 'finmate:session'

export function getSession(): FinMateSession {
  const raw = window.localStorage.getItem(SESSION_KEY)
  if (!raw) {
    return {}
  }
  try {
    return JSON.parse(raw) as FinMateSession
  } catch {
    return {}
  }
}

export function saveSession(next: FinMateSession): FinMateSession {
  const merged = {
    ...getSession(),
    ...next,
  }
  if (next.accessToken) {
    merged.canRefresh = true
  }
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(merged))
  window.dispatchEvent(new Event('finmate-session-change'))
  return merged
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_KEY)
  window.dispatchEvent(new Event('finmate-session-change'))
}

export function accessToken() {
  return getSession().accessToken
}
