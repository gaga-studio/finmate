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

export type OnboardingDiagnosisResponse = {
  diagnosisId: string
  onboardingToken: string
  goalType: string
  recommendedPersonaId: string
  cohortLabel: string
  expiresAt: string
}

export type MockConsentResponse = {
  mydataConnectionId: string
  status: string
  dataMode: string
  agreedAt: string
}

export type PrivacyConsentsResponse = {
  privacySettingsId: string
  consentHistoryIds: string[]
  previewAvailable: boolean
}

export type DemoSessionResponse = {
  userId: string
  accessToken: string
  selectedPersonaId: string
  goalType: string
  expiresAt: string
}

export type HomeResponse = {
  userId: string
  goal: {
    goalType: string
    label: string
  }
  todayBudget: number
  spendingSummary: {
    monthlySpent: number
    fixedCostRatio: number
  }
  assetSummary: {
    cashLikeAssets: number
    emergencyFundMonths: number
  }
  todayMissionCandidate: {
    title: string
    recommendationSource: string
  }
  peerTeaser: {
    portfolioId: string
    title: string
    similarityScore: number
    mainDifference: string
  }
}

export type PortfolioResponse = {
  portfolioId: string
  displayName: string
  status: string
  visibility: string
  dataMode: string
  financialSummary: FinancialSummary
  routineCards: RoutineCard[]
  privacyBadges: string[]
}

export type FinancialSummary = {
  emergencyFundMonths: number
  savingsRate: number
  fixedCostRatio: number
}

export type RoutineCard = {
  title: string
  description: string
}

export type ComparisonResponse = {
  comparisonId: string
  peerPortfolioId: string
  mainGap: {
    type: string
    label: string
    normalizedGap: number
  }
  similarityScore: number
  gapItems: GapItem[]
  nextAction: {
    label: string
    scenarioType: string
  }
}

export type GapItem = {
  type: string
  userValue: number
  peerValue: number
  unit: string
}

export type SimulationResponse = {
  simulationId: string
  comparisonId: string
  scenarioType: string
  periodMonths: number
  monthlyAdditionalSaving: number
  before: SimulationValues
  after: SimulationValues
  insight: string
  nextAction: {
    label: string
    missionTemplateId: string
  }
  disclaimer: string
}

export type SimulationValues = {
  emergencyFundMonths: number
  cashLikeAssets: number
}

export type MissionResponse = {
  missionId: string
  title: string
  description: string
  difficulty: string
  verificationType: string
  rewardPoints: number
  status: string
  localDate: string
  privacySharePreview: {
    shareableText: string
    containsAmount: boolean
  }
}

export type PrivacySettingsResponse = {
  privacySettingsId: string
  anonymousPortfolioOptIn: boolean
  friendShareDefault: string
  ownPortfolioId: string
  exposedFields: string[]
  preview: {
    portfolioId: string
    displayName: string
    hiddenFields: string[]
  }
  consentVersion: string
  updatedAt: string
}

export type PrivacyWithdrawResponse = {
  status: string
  withdrawnAt: string
  affectedPortfolioIds: string[]
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
