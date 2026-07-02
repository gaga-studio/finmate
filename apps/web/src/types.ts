export type FieldError = {
  field: string
  message: string
}

export type ErrorResponse = {
  code: string
  message: string
  fieldErrors?: FieldError[]
}

export type UserMeResponse = {
  userId: string
  email: string
  displayName: string
  onboardingCompleted: boolean
  pointBalance: number
  virtualMoneyBalance: number
}

export type AuthResponse = {
  user: UserMeResponse
  accessToken: string
  expiresAt: string
}

export type ProductPrivacyConsentPayload = {
  anonymousPortfolioOptIn: boolean
  friendShareDefault: string
  exposedFields: string[]
  privacyConsentVersion: string
}

export type ProductMyDataConsentPayload = {
  mydataConsentVersion: string
  mydataScopes: string[]
}

export type ProductOnboardingRequest = {
  ageBand: string
  incomeBand: string
  jobCategory: string
  householdType: string
  moneyStyle: string
  area: string
  goalType: string
  painPoint: string
  privacyConsent: ProductPrivacyConsentPayload
  mydataConsent: ProductMyDataConsentPayload
}

export type AppScreenResponse = {
  screenId: string
  title: string
  tab: 'home' | 'compare' | 'mission' | 'records' | 'profile'
  statusBarTime: string
  heroAsset?: string | null
  sections: AppSection[]
  meta: Record<string, unknown>
}

export type AppSection = {
  id: string
  kind: string
  title: string
  subtitle?: string | null
  detailPath?: string | null
  heroAsset?: string | null
  metrics?: AppMetric[] | null
  items?: AppItem[] | null
  actions?: AppAction[] | null
  data?: Record<string, unknown> | null
}

export type AppMetric = {
  label: string
  value: string
  caption?: string | null
  tone?: string | null
  progress?: number | null
}

export type AppItem = {
  id: string
  title: string
  subtitle?: string | null
  value?: string | null
  caption?: string | null
  icon?: string | null
  tone?: string | null
  detailPath?: string | null
  data?: Record<string, unknown> | null
}

export type AppAction = {
  label: string
  path: string
  method: 'GET' | 'POST'
  tone: string
  intent?: string | null
}

export type AppActionResultResponse = {
  status: string
  title: string
  message: string
  nextPath: string
  data: Record<string, unknown>
}
