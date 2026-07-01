import type {
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
} from './types'

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
  if (options.token) {
    headers.set('Authorization', `Bearer ${options.token}`)
  }
  if (options.idempotencyKey) {
    headers.set('Idempotency-Key', options.idempotencyKey)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
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

const DEMO_ACCESS_TOKEN = 'demo-token'

const appToken = (token?: string) => token ?? DEMO_ACCESS_TOKEN

export const api = {
  health: () => request<{ status: string }>('/health'),
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
    request<AppScreenResponse>('/api/app/home', { token: appToken(token) }),
  getAppHomeDetail: (detail: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/home/${detail}`, { token: appToken(token) }),
  getAppCompare: (token?: string) =>
    request<AppScreenResponse>('/api/app/compare', { token: appToken(token) }),
  getAppCompareFilter: (token?: string) =>
    request<AppScreenResponse>('/api/app/compare/filter', { token: appToken(token) }),
  searchAppCompareFilter: (token?: string) =>
    request<AppScreenResponse>('/api/app/compare/filter/search', {
      method: 'POST',
      token: appToken(token),
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
      token: appToken(token),
    }),
  getAppCoachFlow: (comparisonId = 'cmp-001', token?: string) =>
    request<AppScreenResponse>(`/api/app/compare/${comparisonId}/coach-flow`, {
      token: appToken(token),
    }),
  getAppMissions: (token?: string) =>
    request<AppScreenResponse>('/api/app/missions', { token: appToken(token) }),
  getAppMission: (missionId: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/missions/${missionId}`, { token: appToken(token) }),
  submitAppMissionFeedback: (missionId: string, token?: string) =>
    request<AppActionResultResponse>(`/api/app/missions/${missionId}/feedback`, {
      method: 'POST',
      token: appToken(token),
      body: { status: 'DONE', note: '오늘 목표를 완료했어요.' },
    }),
  getAppRecords: (month = '2026-06', token?: string) =>
    request<AppScreenResponse>(`/api/app/records?month=${month}`, { token: appToken(token) }),
  getAppRecordDetail: (date: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/records/${date}`, { token: appToken(token) }),
  getAppProfile: (token?: string) =>
    request<AppScreenResponse>('/api/app/profile', { token: appToken(token) }),
  getAppProfileSection: (section: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/profile/sections/${section}`, { token: appToken(token) }),
  getAppBirthdays: (token?: string) =>
    request<AppScreenResponse>('/api/app/birthdays', { token: appToken(token) }),
  getAppBirthdayFlow: (birthdayId: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/birthdays/${birthdayId}/flow`, { token: appToken(token) }),
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
      token: appToken(token),
      body: payload,
    }),
  getBirthdayContributionComplete: (fundId: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/birthday-funds/${fundId}/complete`, {
      token: appToken(token),
    }),
  getMyBirthdayFundOpenScreen: (token?: string) =>
    request<AppScreenResponse>('/api/app/birthday-funds/me/open', { token: appToken(token) }),
  openMyBirthdayFund: (token?: string) =>
    request<AppActionResultResponse>('/api/app/birthday-funds/me/open', {
      method: 'POST',
      token: appToken(token),
    }),
  getMyBirthdayFundShareScreen: (token?: string) =>
    request<AppScreenResponse>('/api/app/birthday-funds/me/share', { token: appToken(token) }),
  shareMyBirthdayFund: (token?: string) =>
    request<AppActionResultResponse>('/api/app/birthday-funds/me/share', {
      method: 'POST',
      token: appToken(token),
    }),
  getMyBirthdayFundStatus: (token?: string) =>
    request<AppScreenResponse>('/api/app/birthday-funds/me/status', { token: appToken(token) }),
}
