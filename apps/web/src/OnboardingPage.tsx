import { useState } from 'react'
import { api } from './api'
import { describeError } from './errors'
import type { Navigate } from './navigation'
import { saveSession, type FinMateSession } from './session'
import type { ProductOnboardingRequest } from './types'
import { AppIcon, IconBadge, StatusBar } from './uiPrimitives'

type OnboardingStepIndex = 0 | 1 | 2 | 3

type SurveyState = {
  ageBand: string
  incomeBand: string
  jobCategory: string
  householdType: string
  moneyStyle: string
  area: string
  goalType: string
  painPoint: string
}

type SurveyField = keyof SurveyState

type SurveyOption = {
  value: string
  title: string
  detail: string
  badge?: string
}

type SurveyGroup = {
  field: SurveyField
  title: string
  description: string
  options: SurveyOption[]
}

const onboardingSteps = ['30초 설문', '공개 동의', '데이터 동의', '준비 완료']

const defaultSurvey: SurveyState = {
  ageBand: '20대 후반',
  incomeBand: '3,000만원 ~ 4,000만원',
  jobCategory: 'IT/개발',
  householdType: '1인가구',
  moneyStyle: '안정 추구형',
  area: '서울 강남권',
  goalType: 'EMERGENCY_FUND',
  painPoint: 'SAVE_CONSISTENTLY',
}

const surveyGroups: SurveyGroup[] = [
  {
    field: 'jobCategory',
    title: '현재 하는 일',
    description: '또래 비교 그룹을 잡을 때 가장 먼저 보는 기준이에요.',
    options: [
      { value: 'IT/개발', title: '직장인', detail: '월급 기반 루틴', badge: '추천' },
      { value: '대학생/취준', title: '학생·취준', detail: '생활비 중심' },
      { value: '프리랜서', title: '프리랜서', detail: '월별 수입 변동' },
    ],
  },
  {
    field: 'incomeBand',
    title: '연 소득대',
    description: '정확한 금액 대신 구간만 사용해요.',
    options: [
      { value: '2,000만원 ~ 3,000만원', title: '2,000~3,000만원', detail: '초기 자립 구간' },
      { value: '3,000만원 ~ 4,000만원', title: '3,000~4,000만원', detail: '균형 예산 구간', badge: '선택됨' },
      { value: '4,000만원 ~ 5,000만원', title: '4,000~5,000만원', detail: '저축 여력 구간' },
    ],
  },
  {
    field: 'householdType',
    title: '생활 형태',
    description: '고정 지출과 비상금 목표를 잡는 데 사용해요.',
    options: [
      { value: '1인가구', title: '1인가구', detail: '월세·생활비 직접 관리', badge: '추천' },
      { value: '가족과 거주', title: '가족과 거주', detail: '저축 여력 높음' },
      { value: '동거/룸메이트', title: '동거/룸메이트', detail: '공동 지출 있음' },
    ],
  },
  {
    field: 'moneyStyle',
    title: '소비 성향',
    description: '미션 난이도와 코칭 톤을 맞춰요.',
    options: [
      { value: '안정 추구형', title: '안정 추구형', detail: '비상금과 저축 우선', badge: '추천' },
      { value: '균형형', title: '균형형', detail: '저축과 소비 균형' },
      { value: '투자 적극형', title: '투자 적극형', detail: '자산 성장 관심' },
    ],
  },
  {
    field: 'goalType',
    title: '가장 중요한 목표',
    description: '첫 미션과 홈 우선순위에 반영돼요.',
    options: [
      { value: 'EMERGENCY_FUND', title: '비상금 만들기', detail: '1개월 생활비 준비', badge: '기본' },
      { value: 'INDEPENDENCE', title: '독립 준비', detail: '보증금과 고정비 점검' },
      { value: 'TRAVEL', title: '여행 자금', detail: '단기 목적 저축' },
    ],
  },
  {
    field: 'painPoint',
    title: '요즘 가장 어려운 점',
    description: 'AI 코치가 먼저 볼 문제를 정해요.',
    options: [
      { value: 'SAVE_CONSISTENTLY', title: '꾸준히 모으기', detail: '저축 루틴 만들기', badge: '추천' },
      { value: 'CONTROL_SPENDING', title: '지출 줄이기', detail: '식비·카페비 관리' },
      { value: 'START_INVESTING', title: '투자 시작', detail: '소액 투자 습관' },
    ],
  },
]

const areaOptions: SurveyOption[] = [
  { value: '서울 강남권', title: '서울 강남권', detail: '도시 직장인 비교군', badge: '기본' },
  { value: '서울 마포·성수권', title: '서울 마포·성수권', detail: '콘텐츠·창작 비교군' },
  { value: '수도권', title: '수도권', detail: '통근·생활비 비교군' },
]

const privacyExposedFields = ['ageBand', 'goalType', 'financialSummary', 'missionStatus']

const mydataScopes = [
  { value: 'ACCOUNT_SUMMARY', title: '계좌 요약', detail: '잔액과 입출금 흐름' },
  { value: 'CARD_SPENDING', title: '카드 소비', detail: '식비, 교통, 구독 지출' },
  { value: 'INVESTMENT_SUMMARY', title: '투자 요약', detail: '보유 종목과 평가금액' },
  { value: 'ELECTRONIC_FINANCE', title: '간편결제', detail: '선불·포인트 사용 흐름' },
]

export function OnboardingPage({ navigate, session }: { navigate: Navigate; session: FinMateSession }) {
  const [step, setStep] = useState<OnboardingStepIndex>(0)
  const [survey, setSurvey] = useState<SurveyState>(defaultSurvey)
  const [privacyAgreed, setPrivacyAgreed] = useState(false)
  const [mydataAgreed, setMydataAgreed] = useState(false)
  const [selectedScopes, setSelectedScopes] = useState<string[]>(['ACCOUNT_SUMMARY', 'CARD_SPENDING', 'INVESTMENT_SUMMARY'])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const name = session.user?.displayName ?? 'FinMate'

  const updateSurvey = (field: SurveyField, value: string) => {
    setSurvey((current) => ({ ...current, [field]: value }))
  }

  const toggleScope = (scope: string) => {
    setSelectedScopes((current) => (
      current.includes(scope)
        ? current.filter((item) => item !== scope)
        : [...current, scope]
    ))
  }

  const payload = (): ProductOnboardingRequest => ({
    ...survey,
    privacyConsent: {
      anonymousPortfolioOptIn: privacyAgreed,
      friendShareDefault: 'MISSION_ONLY',
      exposedFields: privacyExposedFields,
      privacyConsentVersion: 'privacy-v1.5',
    },
    mydataConsent: {
      mydataConsentVersion: 'synthetic-mydata-v1.5',
      mydataScopes: selectedScopes,
    },
  })

  const finish = async () => {
    if (!privacyAgreed || !mydataAgreed || selectedScopes.length === 0) {
      setError('필수 동의와 연결 범위를 확인해주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const user = await api.completeOnboarding(payload())
      saveSession({ user })
      navigate('/home')
    } catch (caught) {
      setError(describeError(caught))
    } finally {
      setBusy(false)
    }
  }

  const canGoNext = step === 0 || (step === 1 && privacyAgreed) || (step === 2 && mydataAgreed && selectedScopes.length > 0) || step === 3

  const goNext = () => {
    setError(null)
    if (!canGoNext) {
      setError(step === 1 ? '개인정보 공개 동의가 필요해요.' : '마이데이터 제공 동의와 연결 범위를 확인해주세요.')
      return
    }
    if (step < 3) {
      setStep((step + 1) as OnboardingStepIndex)
      return
    }
    void finish()
  }

  return (
    <div className="screen onboarding-screen">
      <StatusBar time="9:41" />
      <div className="onboarding-shell">
        <header className="onboarding-top">
          <span>FinMate 시작 설정</span>
          <strong>{onboardingSteps[step]}</strong>
          <p>{name}님에게 맞는 비교군, 공개 범위, 금융 데이터 연결 범위를 차례로 정리해요.</p>
        </header>
        <div className="onboarding-progress" aria-label="온보딩 진행 단계">
          {onboardingSteps.map((label, index) => (
            <span className={index <= step ? 'active' : ''} key={label}>
              <b>{index + 1}</b>
              {label}
            </span>
          ))}
        </div>

        {step === 0 ? <SurveyStep survey={survey} updateSurvey={updateSurvey} /> : null}
        {step === 1 ? <PrivacyConsentStep agreed={privacyAgreed} setAgreed={setPrivacyAgreed} survey={survey} /> : null}
        {step === 2 ? <MyDataConsentStep agreed={mydataAgreed} setAgreed={setMydataAgreed} selectedScopes={selectedScopes} toggleScope={toggleScope} /> : null}
        {step === 3 ? <ReadyStep survey={survey} selectedScopes={selectedScopes} /> : null}
      </div>
      {error ? <p className="error-copy">{error}</p> : null}
      <div className="onboarding-actions">
        {step > 0 ? (
          <button className="app-button secondary" type="button" onClick={() => setStep((step - 1) as OnboardingStepIndex)} disabled={busy}>이전</button>
        ) : null}
        <button className="app-button primary" type="button" onClick={goNext} disabled={busy || !canGoNext}>
          {step === 3 ? (busy ? '저장 중' : 'FinMate 시작하기') : '다음'}
        </button>
      </div>
    </div>
  )
}

function SurveyStep({ survey, updateSurvey }: { survey: SurveyState; updateSurvey: (field: SurveyField, value: string) => void }) {
  return (
    <div className="onboarding-content">
      <section className="onboarding-card intro-card">
        <IconBadge icon="spark" tone="purple" />
        <div>
          <h1>30초 설문</h1>
          <p>정확한 계좌 연결 전에도 비슷한 또래와 비교할 수 있도록 최소 정보만 선택해요.</p>
        </div>
      </section>
      {surveyGroups.map((group) => (
        <section className="survey-group" key={group.field}>
          <div className="survey-heading">
            <h2>{group.title}</h2>
            <p>{group.description}</p>
          </div>
          <div className="option-grid">
            {group.options.map((option) => (
              <OptionCard
                key={option.value}
                option={option}
                selected={survey[group.field] === option.value}
                onSelect={() => updateSurvey(group.field, option.value)}
              />
            ))}
          </div>
        </section>
      ))}
      <section className="survey-group">
        <div className="survey-heading">
          <h2>생활권</h2>
          <p>소비 패턴 비교에 쓰이는 지역 범위예요.</p>
        </div>
        <div className="option-grid">
          {areaOptions.map((option) => (
            <OptionCard
              key={option.value}
              option={option}
              selected={survey.area === option.value}
              onSelect={() => updateSurvey('area', option.value)}
            />
          ))}
        </div>
      </section>
    </div>
  )
}

function PrivacyConsentStep({ agreed, setAgreed, survey }: {
  agreed: boolean
  setAgreed: (agreed: boolean) => void
  survey: SurveyState
}) {
  return (
    <div className="onboarding-content">
      <section className="onboarding-card intro-card">
        <IconBadge icon="profile" tone="purple" />
        <div>
          <h1>개인정보 공개 동의</h1>
          <p>또래 비교와 친구 피드에는 익명화된 요약만 보여주고, 민감한 거래 정보는 숨겨요.</p>
        </div>
      </section>
      <section className="consent-preview-card">
        <h2>친구에게 보이는 정보</h2>
        <div className="preview-profile">
          <IconBadge icon="profile" tone="green" />
          <div>
            <strong>익명 포트폴리오</strong>
            <span>{survey.ageBand} · {survey.jobCategory} · {survey.goalType === 'EMERGENCY_FUND' ? '비상금 목표' : '금융 목표'}</span>
          </div>
        </div>
        <div className="privacy-list">
          <span>공개</span>
          <p>연령대, 목표, 자산 요약, 미션 달성 상태</p>
          <span>비공개</span>
          <p>실명, 이메일, 카드번호, 거래처명, 정확한 거래 시각</p>
        </div>
      </section>
      <label className={`consent-check ${agreed ? 'checked' : ''}`}>
        <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
        <span><AppIcon name="check" /></span>
        <strong>익명 비교와 친구 피드 공개 범위에 동의해요</strong>
      </label>
    </div>
  )
}

function MyDataConsentStep({ agreed, setAgreed, selectedScopes, toggleScope }: {
  agreed: boolean
  setAgreed: (agreed: boolean) => void
  selectedScopes: string[]
  toggleScope: (scope: string) => void
}) {
  return (
    <div className="onboarding-content">
      <section className="onboarding-card intro-card">
        <IconBadge icon="chart" tone="green" />
        <div>
          <h1>마이데이터 제공 동의</h1>
          <p>지금은 실제 금융기관 연결이 아니라 합성/샘플 데이터로 앱 흐름을 검증하는 연결이에요.</p>
        </div>
      </section>
      <section className="scope-card">
        <h2>연결할 금융 요약 범위</h2>
        <div className="scope-list">
          {mydataScopes.map((scope) => {
            const selected = selectedScopes.includes(scope.value)
            return (
              <button className={`scope-row ${selected ? 'selected' : ''}`} type="button" onClick={() => toggleScope(scope.value)} key={scope.value}>
                <span><AppIcon name={selected ? 'check' : 'more'} /></span>
                <div>
                  <strong>{scope.title}</strong>
                  <small>{scope.detail}</small>
                </div>
              </button>
            )
          })}
        </div>
      </section>
      <label className={`consent-check ${agreed ? 'checked' : ''}`}>
        <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
        <span><AppIcon name="check" /></span>
        <strong>선택한 범위의 합성 금융 데이터를 FinMate 분석에 사용하는 데 동의해요</strong>
      </label>
    </div>
  )
}

function ReadyStep({ survey, selectedScopes }: { survey: SurveyState; selectedScopes: string[] }) {
  return (
    <div className="onboarding-content">
      <section className="onboarding-card ready-card">
        <img src="/assets/characters/finmate-main.png" alt="" />
        <h1>준비가 끝났어요</h1>
        <p>홈에서 오늘의 미션, 예산, 친구 금융 근황을 바로 확인할 수 있어요.</p>
      </section>
      <section className="ready-summary">
        <h2>저장될 설정</h2>
        <dl>
          <div><dt>비교군</dt><dd>{survey.ageBand} · {survey.jobCategory} · {survey.incomeBand}</dd></div>
          <div><dt>목표</dt><dd>{survey.goalType === 'EMERGENCY_FUND' ? '비상금 1개월 만들기' : survey.goalType}</dd></div>
          <div><dt>연결 범위</dt><dd>{selectedScopes.length}개 금융 요약</dd></div>
        </dl>
      </section>
    </div>
  )
}

function OptionCard({ option, selected, onSelect }: { option: SurveyOption; selected: boolean; onSelect: () => void }) {
  return (
    <button className={`option-card ${selected ? 'selected' : ''}`} type="button" onClick={onSelect}>
      <span>{option.title}</span>
      <strong>{option.detail}</strong>
      {option.badge ? <em>{option.badge}</em> : null}
    </button>
  )
}
