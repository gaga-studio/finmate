import type {
  AuthResponse,
  AppActionResultResponse,
  AppScreenResponse,
  ComparisonResponse,
  DemoSessionResponse,
  ErrorResponse,
  HomeResponse,
  MissionResponse,
  MockConsentResponse,
  OnboardingDiagnosisResponse,
  PortfolioResponse,
  PrivacyConsentsResponse,
  PrivacySettingsResponse,
  PrivacyWithdrawResponse,
  SimulationResponse,
  UserMeResponse,
} from './types'
import { accessToken } from './session'

type BirthdayContributionPayload = {
  amount: number
  message: string
  anonymous: boolean
}

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
).replace(/\/$/, '')

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH'
  token?: string
  body?: unknown
  idempotencyKey?: string
}

export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: ErrorResponse['fieldErrors']

  constructor(status: number, body: ErrorResponse) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.fieldErrors = body.fieldErrors
  }
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({
    Accept: 'application/json',
  })

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  const bearer = options.token ?? accessToken()
  if (bearer) {
    headers.set('Authorization', `Bearer ${bearer}`)
  }
  if (options.idempotencyKey) {
    headers.set('Idempotency-Key', options.idempotencyKey)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    credentials: 'include',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    const body =
      data && typeof data === 'object'
        ? (data as ErrorResponse)
        : {
            code: 'HTTP_ERROR',
            message: `HTTP ${response.status}`,
          }
    throw new ApiError(response.status, body)
  }

  return data as T
}

export const p0Payloads = {
  diagnosis: {
    occupationStatus: 'PART_TIME_STUDENT',
    incomeBand: 'INCOME_150_250',
    householdType: 'SINGLE',
    goalType: 'EMERGENCY_FUND',
    painPoint: 'SAVE_CONSISTENTLY',
  },
  mockConsent: (diagnosisId: string) => ({
    diagnosisId,
    consentVersion: 'mydata-mock-v1.0',
    scopes: ['ACCOUNT_SUMMARY', 'CARD_SPENDING', 'INVESTMENT_SUMMARY'],
  }),
  privacyConsents: {
    anonymousPortfolioOptIn: true,
    friendShareDefault: 'NONE',
    exposedFields: [
      'ageBand',
      'incomeBand',
      'goalType',
      'financialSummary',
      'routineCards',
    ],
    consentVersion: 'privacy-v1.0',
  },
  demoSession: (diagnosisId: string, mydataConnectionId: string) => ({
    mode: 'QUICK_DIAGNOSIS',
    diagnosisId,
    mydataConnectionId,
  }),
  simulation: (comparisonId: string) => ({
    comparisonId,
    scenarioType: 'FOLLOW_PEER_ROUTINE',
    monthlyAdditionalSaving: 100000,
    periodMonths: 3,
  }),
  mission: (simulationId: string, missionTemplateId: string) => ({
    simulationId,
    missionTemplateId,
    triggerSource: 'SIMULATION',
    recommendationSource: 'RULE_BASED',
    difficulty: 'EASY',
  }),
}

export const api = {
  health: () => request<{ status: string }>('/health'),
  signup: (email: string, password: string, displayName: string) =>
    request<AuthResponse>('/api/auth/signup', {
      method: 'POST',
      body: { email, password, displayName },
    }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    }),
  refresh: () =>
    request<AuthResponse>('/api/auth/refresh', {
      method: 'POST',
    }),
  logout: () =>
    request<{ status: string }>('/api/auth/logout', {
      method: 'POST',
    }),
  me: () => request<UserMeResponse>('/api/users/me'),
  completeOnboarding: () =>
    request<UserMeResponse>('/api/users/me/onboarding', {
      method: 'POST',
      body: {
        goalType: 'EMERGENCY_FUND',
        moneyStyle: '안정 추구형',
        householdType: '1인가구',
        area: '서울 강남권',
      },
    }),
  createDiagnosis: () =>
    request<OnboardingDiagnosisResponse>('/api/onboarding/diagnosis', {
      method: 'POST',
      body: p0Payloads.diagnosis,
    }),
  createMockConsent: (token: string, diagnosisId: string) =>
    request<MockConsentResponse>('/api/mydata/mock-consent', {
      method: 'POST',
      token,
      idempotencyKey: 'mydata-mock-001',
      body: p0Payloads.mockConsent(diagnosisId),
    }),
  createPrivacyConsents: (token: string) =>
    request<PrivacyConsentsResponse>('/api/privacy/consents', {
      method: 'POST',
      token,
      idempotencyKey: 'privacy-consents-001',
      body: p0Payloads.privacyConsents,
    }),
  createDemoSession: (
    token: string,
    diagnosisId: string,
    mydataConnectionId: string,
  ) =>
    request<DemoSessionResponse>('/api/demo/session', {
      method: 'POST',
      token,
      idempotencyKey: 'demo-session-001',
      body: p0Payloads.demoSession(diagnosisId, mydataConnectionId),
    }),
  getHome: (token: string) => request<HomeResponse>('/api/home', { token }),
  getPortfolio: (token: string, portfolioId: string) =>
    request<PortfolioResponse>(`/api/explore/portfolios/${portfolioId}`, { token }),
  createComparison: (token: string, peerPortfolioId: string) =>
    request<ComparisonResponse>('/api/comparisons', {
      method: 'POST',
      token,
      body: { peerPortfolioId },
    }),
  createSimulation: (token: string, comparisonId: string) =>
    request<SimulationResponse>('/api/simulations', {
      method: 'POST',
      token,
      body: p0Payloads.simulation(comparisonId),
    }),
  createMission: (
    token: string,
    simulationId: string,
    missionTemplateId: string,
  ) =>
    request<MissionResponse>('/api/missions', {
      method: 'POST',
      token,
      idempotencyKey: 'mission-2026-06-30',
      body: p0Payloads.mission(simulationId, missionTemplateId),
    }),
  getPrivacySettings: (token: string) =>
    request<PrivacySettingsResponse>('/api/privacy/settings', { token }),
  updatePrivacySettings: (token: string) =>
    request<PrivacySettingsResponse>('/api/privacy/settings', {
      method: 'PATCH',
      token,
      body: {
        friendShareDefault: 'MISSION_ONLY',
        exposedFields: ['ageBand', 'goalType', 'financialSummary'],
      },
    }),
  withdrawAnonymousPortfolio: (token: string, portfolioId: string) =>
    request<PrivacyWithdrawResponse>('/api/privacy/withdraw', {
      method: 'POST',
      token,
      body: {
        scope: 'ANONYMOUS_PORTFOLIO',
        portfolioId,
        reason: 'WEB_DEMO_PRIVACY_CHECK',
      },
    }),
  getAppHome: (token?: string) =>
    request<AppScreenResponse>('/api/app/home', { token }),
  getAppHomeDetail: (detail: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/home/${detail}`, { token }),
  getAppCompare: (token?: string) =>
    request<AppScreenResponse>('/api/app/compare', { token }),
  getAppCompareFilter: (token?: string) =>
    request<AppScreenResponse>('/api/app/compare/filter', { token }),
  searchAppCompareFilter: (token?: string) =>
    request<AppScreenResponse>('/api/app/compare/filter/search', {
      method: 'POST',
      token,
      body: {
        ageBand: '20대',
        incomeBand: '3,000만원 ~ 4,000만원',
        jobCategory: 'IT/개발',
        moneyStyle: '안정 추구형',
        area: '서울 강남권',
      },
    }),
  getAppCompareResult: (comparisonId = 'cmp-001', token?: string) =>
    request<AppScreenResponse>(`/api/app/compare/results/${comparisonId}`, {
      token,
    }),
  getAppCoachFlow: (comparisonId = 'cmp-001', token?: string) =>
    request<AppScreenResponse>(`/api/app/compare/${comparisonId}/coach-flow`, {
      token,
    }),
  getAppMissions: (token?: string) =>
    request<AppScreenResponse>('/api/app/missions', { token }),
  getAppMission: (missionId: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/missions/${missionId}`, { token }),
  submitAppMissionFeedback: (missionId: string, token?: string) =>
    request<AppActionResultResponse>(`/api/app/missions/${missionId}/feedback`, {
      method: 'POST',
      token,
      body: { status: 'DONE', note: '오늘 목표를 완료했어요.' },
    }),
  getAppRecords: (month = '2026-06', token?: string) =>
    request<AppScreenResponse>(`/api/app/records?month=${month}`, { token }),
  getAppRecordDetail: (date: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/records/${date}`, { token }),
  getAppProfile: (token?: string) =>
    request<AppScreenResponse>('/api/app/profile', { token }),
  getAppProfileSection: (section: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/profile/sections/${section}`, { token }),
  getAppBirthdays: (token?: string) =>
    request<AppScreenResponse>('/api/app/birthdays', { token }),
  getAppBirthdayFlow: (birthdayId: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/birthdays/${birthdayId}/flow`, { token }),
  contributeBirthdayFund: (
    fundId: string,
    payload: BirthdayContributionPayload = {
      amount: 10000,
      message: '지우야 생일 축하해!',
      anonymous: false,
    },
    token?: string,
  ) =>
    request<AppActionResultResponse>(`/api/app/birthday-funds/${fundId}/contributions`, {
      method: 'POST',
      token,
      body: payload,
    }),
  getBirthdayContributionComplete: (fundId: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/birthday-funds/${fundId}/complete`, {
      token,
    }),
  getMyBirthdayFundOpenScreen: (token?: string) =>
    request<AppScreenResponse>('/api/app/birthday-funds/me/open', { token }),
  openMyBirthdayFund: (token?: string) =>
    request<AppActionResultResponse>('/api/app/birthday-funds/me/open', {
      method: 'POST',
      token,
    }),
  getMyBirthdayFundShareScreen: (token?: string) =>
    request<AppScreenResponse>('/api/app/birthday-funds/me/share', { token }),
  shareMyBirthdayFund: (token?: string) =>
    request<AppActionResultResponse>('/api/app/birthday-funds/me/share', {
      method: 'POST',
      token,
    }),
  getMyBirthdayFundStatus: (token?: string) =>
    request<AppScreenResponse>('/api/app/birthday-funds/me/status', { token }),
}
