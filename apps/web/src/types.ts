export type FieldError = {
  field: string
  message: string
}

export type ErrorResponse = {
  code: string
  message: string
  fieldErrors?: FieldError[]
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
