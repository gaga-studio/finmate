import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { ApiError, api } from './api'
import { clearSession, getSession, saveSession } from './session'
import type {
  ComparisonResponse,
  GapItem,
  HomeResponse,
  MissionResponse,
  PortfolioResponse,
  PrivacySettingsResponse,
  SimulationResponse,
} from './types'
import './App.css'

type Navigate = (path: string) => void

type Route =
  | { name: 'onboarding' }
  | { name: 'home' }
  | { name: 'portfolio'; portfolioId: string }
  | { name: 'compare'; portfolioId: string }
  | { name: 'simulation'; comparisonId: string }
  | { name: 'mission'; simulationId: string }
  | { name: 'privacy' }
  | { name: 'not-found' }

type AsyncState<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; message: string }

type StepStatus = 'pending' | 'loading' | 'done'

type OnboardingStep = {
  label: string
  status: StepStatus
  detail: string
}

type VerificationResult = {
  portfolioId: string
  status: string
  tone: 'ok' | 'warn'
  detail: string
}

const missionTemplateId = 'MISSION_AUTO_TRANSFER_SMALL'
const peerPortfolioId = 'peer-portfolio-023'
const p0SimulationId = 'sim-001'
const emergencyFundTargetCash = 1_000_000
const emergencyFundTargetMonths = 1
const onboardingStatuses = ['학생/알바', '사회초년생', '취준생', '프리랜서']
const onboardingGoals = ['비상금 만들기', '지출 줄이기', '투자 시작 준비']

function parseRoute(pathname: string): Route {
  const parts = pathname.split('/').filter(Boolean)

  if (parts.length === 0 || parts[0] === 'onboarding') {
    return { name: 'onboarding' }
  }
  if (parts[0] === 'home') {
    return { name: 'home' }
  }
  if (parts[0] === 'explore' && parts[1] === 'portfolios' && parts[2]) {
    return { name: 'portfolio', portfolioId: parts[2] }
  }
  if (parts[0] === 'explore' && parts[1] === 'compare' && parts[2]) {
    return { name: 'compare', portfolioId: parts[2] }
  }
  if (parts[0] === 'simulations' && parts[1]) {
    return { name: 'simulation', comparisonId: parts[1] }
  }
  if (parts[0] === 'missions' && parts[1] === 'new' && parts[2]) {
    return { name: 'mission', simulationId: parts[2] }
  }
  if (parts[0] === 'settings' && parts[1] === 'privacy') {
    return { name: 'privacy' }
  }
  return { name: 'not-found' }
}

function usePathname(): [string, Navigate] {
  const [pathname, setPathname] = useState(window.location.pathname)

  useEffect(() => {
    const handlePopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const navigate: Navigate = (path) => {
    window.history.pushState({}, '', path)
    setPathname(path)
  }

  return [pathname, navigate]
}

function useAsyncData<T>(loader: () => Promise<T>): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' })

  useEffect(() => {
    let active = true
    setState({ status: 'loading' })
    loader()
      .then((data) => {
        if (active) {
          setState({ status: 'success', data })
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setState({ status: 'error', message: describeError(error) })
        }
      })
    return () => {
      active = false
    }
  }, [loader])

  return state
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = error.fieldErrors?.map((item) => item.message).join(' ')
    return fields || error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return '알 수 없는 오류가 발생했어요.'
}

function formatKrw(value: number): string {
  return `${value.toLocaleString('ko-KR')}원`
}

function formatKrwCompact(value: number): string {
  if (value >= 10_000 && value % 10_000 === 0) {
    return `${(value / 10_000).toLocaleString('ko-KR')}만 원`
  }
  return formatKrw(value)
}

function formatRatio(value: number): string {
  return `${Math.round(value * 100)}%`
}

function formatMonths(value: number): string {
  return `${value.toFixed(1)}개월`
}

function App() {
  const [pathname, navigate] = usePathname()
  const route = parseRoute(pathname)
  const showTabs = route.name !== 'onboarding' && route.name !== 'not-found'

  return (
    <div className="app-canvas">
      <div className="app-shell">
        <Header route={route} navigate={navigate} />
        <main className="app-main">{renderRoute(route, navigate)}</main>
        {showTabs ? <BottomNav route={route} navigate={navigate} /> : null}
      </div>
    </div>
  )
}

function Header({ route, navigate }: { route: Route; navigate: Navigate }) {
  const session = getSession()
  const title = getHeaderTitle(route)

  return (
    <header className="mobile-header">
      <button className="brand-button" type="button" onClick={() => navigate('/home')}>
        <span className="brand-mark" aria-hidden="true">
          F
        </span>
        <span>
          <strong>FinMate</strong>
          <small>{session.accessToken ? '비상금 루틴 진행 중' : '합성 데이터 체험'}</small>
        </span>
      </button>
      <div className="header-context">
        <span>{title}</span>
      </div>
    </header>
  )
}

function getHeaderTitle(route: Route): string {
  switch (route.name) {
    case 'onboarding':
      return '시작하기'
    case 'home':
      return '홈'
    case 'portfolio':
      return '또래 사례'
    case 'compare':
      return '비교'
    case 'simulation':
      return '시뮬레이션'
    case 'mission':
      return '미션'
    case 'privacy':
      return '공개 설정'
    case 'not-found':
      return '안내'
  }
}

function BottomNav({ route, navigate }: { route: Route; navigate: Navigate }) {
  const items = [
    { label: '홈', icon: 'home', path: '/home', active: route.name === 'home' },
    {
      label: '또래',
      icon: 'users',
      path: `/explore/portfolios/${peerPortfolioId}`,
      active: route.name === 'portfolio' || route.name === 'compare',
    },
    {
      label: '미션',
      icon: 'target',
      path: `/missions/new/${p0SimulationId}`,
      active: route.name === 'mission' || route.name === 'simulation',
    },
    {
      label: '설정',
      icon: 'lock',
      path: '/settings/privacy',
      active: route.name === 'privacy',
    },
  ]

  return (
    <nav className="bottom-nav" aria-label="주요 화면">
      {items.map((item) => (
        <button
          className={item.active ? 'active' : undefined}
          type="button"
          key={item.label}
          onClick={() => navigate(item.path)}
        >
          <Icon name={item.icon} />
          <span>{item.label}</span>
        </button>
      ))}
    </nav>
  )
}

function renderRoute(route: Route, navigate: Navigate): ReactNode {
  switch (route.name) {
    case 'onboarding':
      return <OnboardingPage navigate={navigate} />
    case 'home':
      return <ProtectedPage navigate={navigate} render={(token) => <HomePage token={token} navigate={navigate} />} />
    case 'portfolio':
      return (
        <ProtectedPage
          navigate={navigate}
          render={(token) => (
            <PortfolioPage token={token} portfolioId={route.portfolioId} navigate={navigate} />
          )}
        />
      )
    case 'compare':
      return (
        <ProtectedPage
          navigate={navigate}
          render={(token) => (
            <ComparisonPage token={token} portfolioId={route.portfolioId} navigate={navigate} />
          )}
        />
      )
    case 'simulation':
      return (
        <ProtectedPage
          navigate={navigate}
          render={(token) => (
            <SimulationPage token={token} comparisonId={route.comparisonId} navigate={navigate} />
          )}
        />
      )
    case 'mission':
      return (
        <ProtectedPage
          navigate={navigate}
          render={(token) => (
            <MissionPage token={token} simulationId={route.simulationId} navigate={navigate} />
          )}
        />
      )
    case 'privacy':
      return <ProtectedPage navigate={navigate} render={(token) => <PrivacyPage token={token} />} />
    case 'not-found':
      return (
        <EmptyState
          title="화면을 찾을 수 없어요"
          body="체험을 다시 시작하면 FinMate 흐름을 처음부터 볼 수 있어요."
          actionLabel="시작 화면으로 이동"
          onAction={() => navigate('/onboarding')}
        />
      )
  }
}

function ProtectedPage({
  navigate,
  render,
}: {
  navigate: Navigate
  render: (token: string) => ReactNode
}) {
  const session = getSession()
  if (!session.accessToken) {
    return (
      <EmptyState
        title="체험을 먼저 시작해주세요"
        body="합성 마이데이터 연결과 공개 범위 확인을 마치면 앱 화면을 볼 수 있어요."
        actionLabel="시작하기"
        onAction={() => navigate('/onboarding')}
      />
    )
  }

  return render(session.accessToken)
}

function OnboardingPage({ navigate }: { navigate: Navigate }) {
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedStatus, setSelectedStatus] = useState(onboardingStatuses[0])
  const [selectedGoal, setSelectedGoal] = useState(onboardingGoals[0])
  const [steps, setSteps] = useState<OnboardingStep[]>([
    { label: '지금 상태 선택', status: 'pending', detail: '학생/알바 · 사회초년생 · 취준생 · 프리랜서' },
    { label: '금융 목표 선택', status: 'pending', detail: '비상금 만들기 · 지출 줄이기 · 투자 시작 준비' },
    { label: '체험용 데이터 안내', status: 'pending', detail: '실제 금융정보가 아닌 합성 데이터로 시연됩니다' },
    { label: '공개 미리보기 동의', status: 'pending', detail: '공개 전 미리보고 언제든 철회할 수 있어요' },
  ])

  const markStep = (index: number, status: StepStatus, detail: string) => {
    setSteps((current) =>
      current.map((step, stepIndex) =>
        stepIndex === index ? { ...step, status, detail } : step,
      ),
    )
  }

  const startOnboarding = async () => {
    clearSession()
    setError(null)
    setRunning(true)
    try {
      markStep(0, 'loading', '선택한 상태를 확인하고 있어요')
      const diagnosis = await api.createDiagnosis()
      saveSession({
        diagnosisId: diagnosis.diagnosisId,
        onboardingToken: diagnosis.onboardingToken,
        goalType: diagnosis.goalType,
      })
      markStep(0, 'done', `${selectedStatus} 기준으로 준비했어요`)

      markStep(1, 'loading', '선택한 목표를 연결하고 있어요')
      const mockConsent = await api.createMockConsent(
        diagnosis.onboardingToken,
        diagnosis.diagnosisId,
      )
      saveSession({ mydataConnectionId: mockConsent.mydataConnectionId })
      markStep(1, 'done', `${selectedGoal} 목표로 체험합니다`)

      markStep(2, 'loading', '합성 마이데이터를 연결하고 있어요')
      const privacy = await api.createPrivacyConsents(diagnosis.onboardingToken)
      saveSession({ privacySettingsId: privacy.privacySettingsId })
      markStep(2, 'done', '실제 금융정보 없이 연결 완료')

      markStep(3, 'loading', '공개 전 미리보기를 만들고 있어요')
      const demoSession = await api.createDemoSession(
        diagnosis.onboardingToken,
        diagnosis.diagnosisId,
        mockConsent.mydataConnectionId,
      )
      saveSession({
        userId: demoSession.userId,
        accessToken: demoSession.accessToken,
        selectedPersonaId: demoSession.selectedPersonaId,
        goalType: demoSession.goalType,
      })
      markStep(3, 'done', '보이는 정보와 숨김 정보 확인 완료')
      navigate('/home')
    } catch (caught) {
      setError(describeError(caught))
    } finally {
      setRunning(false)
    }
  }

  return (
    <section className="onboarding-screen">
      <div className="hero-copy">
        <p className="section-label">FinMate</p>
        <h1>나에게 맞는 비상금 루틴을 찾아볼까요?</h1>
        <p>
          실제 금융정보를 쓰지 않고, 합성 마이데이터로 또래 사례와 미션 흐름을
          체험합니다.
        </p>
      </div>

      <div className="onboarding-flow">
        <article className="choice-card onboarding-step-card">
          <StepTitle index={1} step={steps[0]} />
          <div className="choice-grid">
            {onboardingStatuses.map((status) => (
              <button
                className={`selectable-chip ${selectedStatus === status ? 'selected' : ''}`}
                type="button"
                key={status}
                onClick={() => setSelectedStatus(status)}
                disabled={running}
              >
                {status}
              </button>
            ))}
          </div>
        </article>

        <article className="choice-card onboarding-step-card">
          <StepTitle index={2} step={steps[1]} />
          <div className="choice-grid">
            {onboardingGoals.map((goal) => (
              <button
                className={`selectable-chip ${selectedGoal === goal ? 'selected' : ''}`}
                type="button"
                key={goal}
                onClick={() => setSelectedGoal(goal)}
                disabled={running}
              >
                {goal}
              </button>
            ))}
          </div>
        </article>

        <article className="panel onboarding-step-card">
          <StepTitle index={3} step={steps[2]} />
          <p>실제 금융정보가 아닌 합성 데이터로 또래 비교와 미션 흐름을 시연합니다.</p>
        </article>

        <article className="panel onboarding-step-card">
          <StepTitle index={4} step={steps[3]} />
          <p>이름, 계좌번호, 거래처, 정확한 거래 시간은 공개 카드에 보이지 않아요.</p>
        </article>
      </div>

      {error ? <InlineError message={error} /> : null}

      <StickyAction>
        <button className="primary-button" type="button" onClick={startOnboarding} disabled={running}>
          {running ? '준비 중이에요' : '동의하고 시작하기'}
        </button>
        <button type="button" onClick={() => navigate('/home')}>
          이어서 보기
        </button>
      </StickyAction>
    </section>
  )
}

function HomePage({ token, navigate }: { token: string; navigate: Navigate }) {
  const loadHome = useCallback(() => api.getHome(token), [token])
  const state = useAsyncData<HomeResponse>(loadHome)

  return (
    <AsyncBoundary state={state}>
      {(home) => {
        const remainingCash = Math.max(0, emergencyFundTargetCash - home.assetSummary.cashLikeAssets)

        return (
          <section className="stack">
            <div className="hero-copy compact">
              <p className="section-label">오늘의 홈</p>
              <h1>오늘은 비상금 루틴을 시작하기 좋은 날이에요</h1>
              <p>{home.goal.label}까지 남은 거리를 한눈에 확인해요.</p>
            </div>

            <article className="hero-card">
              <span>비상금 준비율</span>
              <strong>{formatMonths(home.assetSummary.emergencyFundMonths)}</strong>
              <p>목표 1개월까지 {formatKrwCompact(remainingCash)} 남았어요.</p>
              <ProgressBar
                value={home.assetSummary.emergencyFundMonths}
                max={emergencyFundTargetMonths}
              />
            </article>

            <div className="metric-grid">
              <MetricCard label="오늘 예산" value={formatKrw(home.todayBudget)} />
              <MetricCard label="이번 달 소비" value={formatKrwCompact(home.spendingSummary.monthlySpent)} />
              <MetricCard label="현금성 자산" value={formatKrwCompact(home.assetSummary.cashLikeAssets)} />
              <MetricCard label="고정비 비중" value={formatRatio(home.spendingSummary.fixedCostRatio)} />
            </div>

            <InsightCard
              label="오늘의 미션 후보"
              title={home.todayMissionCandidate.title}
              body="작게 먼저 떼어두는 루틴으로 비상금 목표에 가까워질 수 있어요."
              actionLabel="미션 만들기"
              onAction={() => navigate(`/missions/new/${p0SimulationId}`)}
            />

            <InsightCard
              tone="accent"
              label="비슷한 또래 사례"
              title={home.peerTeaser.title}
              body={`${home.peerTeaser.mainDifference}예요. 또래의 루틴을 보고 내 데이터와 비교해보세요.`}
              meta={`비슷한 맥락 ${formatRatio(home.peerTeaser.similarityScore)}`}
              actionLabel="또래 사례 보기"
              onAction={() => navigate(`/explore/portfolios/${home.peerTeaser.portfolioId}`)}
            />
          </section>
        )
      }}
    </AsyncBoundary>
  )
}

function PortfolioPage({
  token,
  portfolioId,
  navigate,
}: {
  token: string
  portfolioId: string
  navigate: Navigate
}) {
  const loadPortfolio = useCallback(
    () => api.getPortfolio(token, portfolioId),
    [token, portfolioId],
  )
  const state = useAsyncData<PortfolioResponse>(loadPortfolio)

  return (
    <AsyncBoundary state={state}>
      {(portfolio) => (
        <section className="stack">
          <article className="profile-card">
            <div className="profile-avatar" aria-hidden="true">
              B
            </div>
            <div>
              <p className="section-label">익명 또래 포트폴리오</p>
              <h1>{portfolio.displayName}</h1>
              <p>사회초년생 · 1인가구 · 비상금 목표</p>
            </div>
            <span className="mode-pill">{formatDataMode(portfolio.dataMode)}</span>
          </article>

          <div className="chip-row">
            {portfolio.privacyBadges.map((badge) => (
              <span className="chip safe" key={badge}>
                {badge}
              </span>
            ))}
          </div>

          <div className="metric-grid">
            <MetricCard
              label="비상금 준비율"
              value={formatMonths(portfolio.financialSummary.emergencyFundMonths)}
            />
            <MetricCard label="저축률" value={formatRatio(portfolio.financialSummary.savingsRate)} />
            <MetricCard
              label="고정비 비중"
              value={formatRatio(portfolio.financialSummary.fixedCostRatio)}
            />
          </div>

          <section className="story-section">
            <div className="section-heading">
              <h2>루틴 스토리</h2>
              <span>금액보다 행동 중심으로 보여줘요</span>
            </div>
            <div className="story-rail">
              {portfolio.routineCards.map((card, index) => (
                <article className="story-card" key={card.title}>
                  <span>{index + 1}</span>
                  <strong>{card.title}</strong>
                  <p>{card.description}</p>
                </article>
              ))}
            </div>
          </section>

          <StickyAction>
            <button
              className="primary-button"
              type="button"
              onClick={() => navigate(`/explore/compare/${portfolio.portfolioId}`)}
            >
              내 데이터와 비교하기
            </button>
          </StickyAction>
        </section>
      )}
    </AsyncBoundary>
  )
}

function ComparisonPage({
  token,
  portfolioId,
  navigate,
}: {
  token: string
  portfolioId: string
  navigate: Navigate
}) {
  const loadComparison = useCallback(
    () => api.createComparison(token, portfolioId),
    [token, portfolioId],
  )
  const state = useAsyncData<ComparisonResponse>(loadComparison)

  return (
    <AsyncBoundary state={state}>
      {(comparison) => {
        const emergencyGap = comparison.gapItems.find((item) => item.type === 'EMERGENCY_FUND')

        return (
          <section className="stack">
            <article className="hero-card comparison-hero">
              <span>가장 큰 차이</span>
              <strong>{comparison.mainGap.label}</strong>
              {emergencyGap ? (
                <p>
                  나 {formatMonths(emergencyGap.userValue)} vs 또래{' '}
                  {formatMonths(emergencyGap.peerValue)}. 약{' '}
                  {formatMonths(Math.abs(emergencyGap.peerValue - emergencyGap.userValue))} 차이예요.
                </p>
              ) : null}
              <small>비슷한 맥락의 또래 사례 {formatRatio(comparison.similarityScore)}</small>
            </article>

            {emergencyGap ? <CompareBars item={emergencyGap} /> : null}

            <section className="gap-list">
              <div className="section-heading">
                <h2>차이를 만든 항목</h2>
                <span>내 값과 또래 값을 나란히 봅니다</span>
              </div>
              {comparison.gapItems.map((item) => (
                <GapCard item={item} key={item.type} />
              ))}
            </section>

            <StickyAction>
              <button
                className="primary-button"
                type="button"
                onClick={() => navigate(`/simulations/${comparison.comparisonId}`)}
              >
                {comparison.nextAction.label}
              </button>
            </StickyAction>
          </section>
        )
      }}
    </AsyncBoundary>
  )
}

function SimulationPage({
  token,
  comparisonId,
  navigate,
}: {
  token: string
  comparisonId: string
  navigate: Navigate
}) {
  const loadSimulation = useCallback(
    () => api.createSimulation(token, comparisonId),
    [token, comparisonId],
  )
  const state = useAsyncData<SimulationResponse>(loadSimulation)

  return (
    <AsyncBoundary state={state}>
      {(simulation) => (
        <section className="stack">
          <div className="hero-copy compact">
            <p className="section-label">3개월 시뮬레이션</p>
            <h1>작게 먼저 떼어두면 이렇게 달라져요</h1>
            <p>{simulation.insight}</p>
          </div>

          <article className="simulation-card">
            <ValueCard title="현재" values={simulation.before} />
            <span className="arrow-chip" aria-hidden="true">
              →
            </span>
            <ValueCard title="3개월 뒤" values={simulation.after} highlight />
          </article>

          <article className="panel progress-panel">
            <div className="section-heading">
              <h2>목표 1개월 대비</h2>
              <span>
                {formatRatio(simulation.before.emergencyFundMonths / emergencyFundTargetMonths)} →{' '}
                {formatRatio(simulation.after.emergencyFundMonths / emergencyFundTargetMonths)}
              </span>
            </div>
            <ProgressPair
              before={simulation.before.emergencyFundMonths}
              after={simulation.after.emergencyFundMonths}
              max={emergencyFundTargetMonths}
            />
            <p className="hint">
              매월 추가 저축 {formatKrwCompact(simulation.monthlyAdditionalSaving)} · 기간{' '}
              {simulation.periodMonths}개월
            </p>
          </article>

          <p className="disclaimer">{simulation.disclaimer}</p>

          <StickyAction>
            <button
              className="primary-button"
              type="button"
              onClick={() => navigate(`/missions/new/${simulation.simulationId}`)}
            >
              {simulation.nextAction.label}
            </button>
          </StickyAction>
        </section>
      )}
    </AsyncBoundary>
  )
}

function MissionPage({
  token,
  simulationId,
  navigate,
}: {
  token: string
  simulationId: string
  navigate: Navigate
}) {
  const loadMission = useCallback(
    () => api.createMission(token, simulationId, missionTemplateId),
    [token, simulationId],
  )
  const state = useAsyncData<MissionResponse>(loadMission)

  return (
    <AsyncBoundary state={state}>
      {(mission) => (
        <section className="stack">
          <article className="mission-card">
            <p className="section-label">오늘의 미션</p>
            <h1>{mission.title}</h1>
            <p>{mission.description}</p>
            <div className="badge-row">
              <span>{formatDifficulty(mission.difficulty)}</span>
              <span>{formatVerificationType(mission.verificationType)}</span>
              <span>{mission.rewardPoints}P</span>
            </div>
          </article>

          <article className="panel share-preview">
            <span>공유 미리보기</span>
            <h2>{mission.privacySharePreview.shareableText}</h2>
            <p>
              {mission.privacySharePreview.containsAmount
                ? '공유 전 금액 표시 여부를 다시 확인해주세요.'
                : '금액 정보는 포함되지 않아요.'}
            </p>
          </article>

          <StickyAction>
            <button type="button" onClick={() => navigate('/home')}>
              홈으로 이동
            </button>
            <button
              className="primary-button"
              type="button"
              onClick={() => navigate('/settings/privacy')}
            >
              공개 범위 확인
            </button>
          </StickyAction>
        </section>
      )}
    </AsyncBoundary>
  )
}

function PrivacyPage({ token }: { token: string }) {
  const [refreshKey, setRefreshKey] = useState(0)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [verification, setVerification] = useState<VerificationResult[]>([])
  const loadPrivacySettings = useCallback(() => {
    void refreshKey
    return api.getPrivacySettings(token)
  }, [token, refreshKey])
  const state = useAsyncData<PrivacySettingsResponse>(loadPrivacySettings)

  const saveMissionOnly = async () => {
    setActionMessage(null)
    setVerification([])
    try {
      await api.updatePrivacySettings(token)
      setActionMessage('공유 기본값을 미션 공유로 저장했어요.')
      setRefreshKey((key) => key + 1)
    } catch (caught) {
      setActionMessage(describeError(caught))
    }
  }

  const withdraw = async (ownPortfolioId: string) => {
    setActionMessage(null)
    setVerification([])
    try {
      await api.withdrawAnonymousPortfolio(token, ownPortfolioId)
      const own = await verifyPortfolio(token, ownPortfolioId)
      const peer = await verifyPortfolio(token, peerPortfolioId)
      setActionMessage('공개 동의가 철회됐어요. 내 익명 포트폴리오는 더 이상 탐색되지 않습니다.')
      setVerification([own, peer])
      setRefreshKey((key) => key + 1)
    } catch (caught) {
      setActionMessage(describeError(caught))
    }
  }

  return (
    <AsyncBoundary state={state}>
      {(settings) => (
        <section className="stack">
          <article className="panel privacy-preview">
            <p className="section-label">공개 미리보기</p>
            <h1>{settings.preview.displayName}</h1>
            <p>내 익명 포트폴리오에는 필요한 요약 정보만 보여요.</p>
          </article>

          <div className="privacy-grid">
            <article className="panel">
              <h2>보이는 정보</h2>
              <div className="chip-row">
                {settings.exposedFields.map((field) => (
                  <span className="chip safe" key={field}>
                    {formatPrivacyField(field)}
                  </span>
                ))}
              </div>
            </article>

            <article className="panel">
              <h2>숨겨지는 정보</h2>
              <div className="chip-row">
                {settings.preview.hiddenFields.map((field) => (
                  <span className="chip muted" key={field}>
                    {formatPrivacyField(field)}
                  </span>
                ))}
              </div>
            </article>
          </div>

          <article className="panel">
            <h2>기본 공유 범위</h2>
            <p>친구에게는 미션 관련 내용만 기본으로 공유되도록 바꿀 수 있어요.</p>
            <button type="button" onClick={saveMissionOnly}>
              미션 공유로 저장
            </button>
          </article>

          <article className="panel danger-panel">
            <h2>공개 철회</h2>
            <p>철회하면 내 익명 포트폴리오는 더 이상 또래 탐색 화면에 노출되지 않아요.</p>
            <button
              className="danger-button"
              type="button"
              onClick={() => withdraw(settings.ownPortfolioId)}
            >
              익명 포트폴리오 공개 철회
            </button>
          </article>

          {actionMessage ? <p className="toast">{actionMessage}</p> : null}
          {verification.length > 0 ? (
            <DebugPanel title="데모 검증 정보">
              <div className="table-list">
                {verification.map((item) => (
                  <div className={`table-row ${item.tone}`} key={item.portfolioId}>
                    <strong>{item.portfolioId}</strong>
                    <span>{item.status}</span>
                    <span>{item.detail}</span>
                  </div>
                ))}
              </div>
            </DebugPanel>
          ) : null}
        </section>
      )}
    </AsyncBoundary>
  )
}

async function verifyPortfolio(token: string, portfolioId: string): Promise<VerificationResult> {
  try {
    await api.getPortfolio(token, portfolioId)
    return {
      portfolioId,
      status: '200 OK',
      tone: 'ok',
      detail: '조회 가능',
    }
  } catch (caught) {
    if (caught instanceof ApiError) {
      return {
        portfolioId,
        status: `${caught.status} ${caught.code}`,
        tone: caught.status === 410 ? 'warn' : 'ok',
        detail: caught.message,
      }
    }
    return {
      portfolioId,
      status: 'ERROR',
      tone: 'warn',
      detail: describeError(caught),
    }
  }
}

function AsyncBoundary<T>({
  state,
  children,
}: {
  state: AsyncState<T>
  children: (data: T) => ReactNode
}) {
  if (state.status === 'loading') {
    return <EmptyState title="데이터를 준비하고 있어요" body="잠시만 기다려주세요." />
  }
  if (state.status === 'error') {
    return <EmptyState title="화면을 불러오지 못했어요" body={state.message} />
  }
  return children(state.data)
}

function EmptyState({
  title,
  body,
  actionLabel,
  onAction,
}: {
  title: string
  body: string
  actionLabel?: string
  onAction?: () => void
}) {
  return (
    <section className="empty-state">
      <h1>{title}</h1>
      <p>{body}</p>
      {actionLabel && onAction ? (
        <button className="primary-button" type="button" onClick={onAction}>
          {actionLabel}
        </button>
      ) : null}
    </section>
  )
}

function InlineError({ message }: { message: string }) {
  return <p className="inline-error">{message}</p>
}

function StickyAction({ children }: { children: ReactNode }) {
  return <div className="sticky-action">{children}</div>
}

function StepTitle({ index, step }: { index: number; step: OnboardingStep }) {
  return (
    <div className="step-title">
      <span className={`status-dot ${step.status}`} />
      <div>
        <strong>
          {index}. {step.label}
        </strong>
        <small>{step.detail}</small>
      </div>
    </div>
  )
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  )
}

function InsightCard({
  label,
  title,
  body,
  meta,
  actionLabel,
  onAction,
  tone = 'default',
}: {
  label: string
  title: string
  body: string
  meta?: string
  actionLabel: string
  onAction: () => void
  tone?: 'default' | 'accent'
}) {
  return (
    <article className={`insight-card ${tone}`}>
      <span>{label}</span>
      <h2>{title}</h2>
      <p>{body}</p>
      {meta ? <small>{meta}</small> : null}
      <button className="primary-button" type="button" onClick={onAction}>
        {actionLabel}
      </button>
    </article>
  )
}

function ProgressBar({ value, max }: { value: number; max: number }) {
  const width = `${Math.min(100, Math.max(0, (value / max) * 100))}%`
  return (
    <div className="progress-track" aria-hidden="true">
      <span style={{ width }} />
    </div>
  )
}

function ProgressPair({ before, after, max }: { before: number; after: number; max: number }) {
  return (
    <div className="progress-pair">
      <div>
        <span>
          현재 {formatMonths(before)} · 목표 대비 {formatRatio(before / max)}
        </span>
        <ProgressBar value={before} max={max} />
      </div>
      <div>
        <span>
          3개월 뒤 {formatMonths(after)} · 목표 대비 {formatRatio(after / max)}
        </span>
        <ProgressBar value={after} max={max} />
      </div>
    </div>
  )
}

function CompareBars({ item }: { item: GapItem }) {
  const max = Math.max(item.userValue, item.peerValue, 1)
  return (
    <article className="panel compare-bars">
      <div className="section-heading">
        <h2>{formatGapLabel(item.type)} 비교</h2>
        <span>
          나 {formatGapValue(item.userValue, item.unit)} · 또래{' '}
          {formatGapValue(item.peerValue, item.unit)}
        </span>
      </div>
      <div className="compare-row">
        <span>나</span>
        <div className="bar-track">
          <span style={{ width: `${(item.userValue / max) * 100}%` }} />
        </div>
        <strong>{formatGapValue(item.userValue, item.unit)}</strong>
      </div>
      <div className="compare-row peer">
        <span>또래</span>
        <div className="bar-track">
          <span style={{ width: `${(item.peerValue / max) * 100}%` }} />
        </div>
        <strong>{formatGapValue(item.peerValue, item.unit)}</strong>
      </div>
    </article>
  )
}

function GapCard({ item }: { item: GapItem }) {
  return (
    <article className="gap-card">
      <div>
        <strong>{formatGapLabel(item.type)}</strong>
        <span>{formatGapValue(Math.abs(item.peerValue - item.userValue), item.unit)} 차이</span>
      </div>
      <div>
        <span>나 {formatGapValue(item.userValue, item.unit)}</span>
        <span>또래 {formatGapValue(item.peerValue, item.unit)}</span>
      </div>
    </article>
  )
}

function ValueCard({
  title,
  values,
  highlight = false,
}: {
  title: string
  values: SimulationResponse['before']
  highlight?: boolean
}) {
  return (
    <article className={`value-card ${highlight ? 'highlight' : ''}`}>
      <span>{title}</span>
      <strong>{formatKrwCompact(values.cashLikeAssets)}</strong>
      <p>{formatMonths(values.emergencyFundMonths)}</p>
    </article>
  )
}

function DebugPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <details className="debug-panel">
      <summary>{title}</summary>
      {children}
    </details>
  )
}

function Icon({ name }: { name: string }) {
  switch (name) {
    case 'home':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z" />
        </svg>
      )
    case 'users':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M8.5 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm7-1a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM3 20c.5-3.4 2.4-5.2 5.5-5.2S13.5 16.6 14 20H3Zm11.6 0c-.2-1.5-.7-2.8-1.5-3.8.7-.5 1.6-.8 2.7-.8 2.7 0 4.4 1.6 4.9 4.6h-6.1Z" />
        </svg>
      )
    case 'target':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 21a9 9 0 1 1 9-9h-2.2a6.8 6.8 0 1 0-6.8 6.8V21Zm0-4.4a4.6 4.6 0 1 1 4.6-4.6h-2.2a2.4 2.4 0 1 0-2.4 2.4v2.2Zm0-4.1a.5.5 0 1 1 .5-.5h2.2a2.7 2.7 0 1 0-2.7 2.7v-2.2Z" />
        </svg>
      )
    case 'lock':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M7 10V8a5 5 0 0 1 10 0v2h1a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1h1Zm2 0h6V8a3 3 0 0 0-6 0v2Zm3 3.5a1.5 1.5 0 0 0-.8 2.8V18h1.6v-1.7a1.5 1.5 0 0 0-.8-2.8Z" />
        </svg>
      )
    default:
      return null
  }
}

function formatGapValue(value: number, unit: string): string {
  if (unit === 'ratio') {
    return formatRatio(value)
  }
  if (unit === 'months') {
    return formatMonths(value)
  }
  return String(value)
}

function formatGapLabel(type: string): string {
  const labels: Record<string, string> = {
    EMERGENCY_FUND: '비상금 준비율',
    SAVINGS_RATE: '저축률',
    FIXED_COST_RATIO: '고정비 비중',
  }
  return labels[type] ?? type
}

function formatDataMode(mode: string): string {
  const labels: Record<string, string> = {
    SYNTHETIC_PERSONA: '합성 페르소나',
    MOCK_MYDATA: '합성 마이데이터',
  }
  return labels[mode] ?? mode
}

function formatDifficulty(value: string): string {
  return value === 'EASY' ? '난이도 쉬움' : value
}

function formatVerificationType(value: string): string {
  return value === 'SELF_CHECK' ? '자가 체크' : value
}

function formatPrivacyField(field: string): string {
  const labels: Record<string, string> = {
    ageBand: '연령대',
    incomeBand: '소득 구간',
    goalType: '금융 목표',
    financialSummary: '금융 요약',
    routineCards: '루틴 카드',
    name: '이름',
    accountNumber: '계좌번호',
    merchantName: '거래처',
    exactTransactionTime: '정확한 거래 시간',
  }
  return labels[field] ?? field
}

export default App
