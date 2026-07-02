import type {
  AuthResponse,
  AppActionResultResponse,
  AppScreenResponse,
  ErrorResponse,
  ProductOnboardingRequest,
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
  completeOnboarding: (body: ProductOnboardingRequest) =>
    request<UserMeResponse>('/api/users/me/onboarding', {
      method: 'POST',
      body,
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
  getAppMissionAdd: (token?: string) =>
    request<AppScreenResponse>('/api/app/missions/add', { token }),
  getAppMission: (missionId: string, token?: string) =>
    request<AppScreenResponse>(`/api/app/missions/${missionId}`, { token }),
  addAppMissionFromTemplate: (templateId: string, token?: string) =>
    request<AppActionResultResponse>(`/api/app/missions/add/${templateId}`, {
      method: 'POST',
      token,
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
