export type FinMateSession = {
  diagnosisId?: string
  onboardingToken?: string
  mydataConnectionId?: string
  privacySettingsId?: string
  userId?: string
  accessToken?: string
  selectedPersonaId?: string
  goalType?: string
}

const SESSION_KEY = 'finmate:p0-session'

export function getSession(): FinMateSession {
  const raw = window.sessionStorage.getItem(SESSION_KEY)
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
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(merged))
  return merged
}

export function clearSession() {
  window.sessionStorage.removeItem(SESSION_KEY)
}
