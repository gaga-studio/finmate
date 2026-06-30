import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { API_BASE_URL, ApiError, api } from './api'
import { clearSession, getSession, saveSession } from './session'
import type {
  ComparisonResponse,
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

type VerificationResult = {
  portfolioId: string
  status: string
  tone: 'ok' | 'warn'
  detail: string
}

const missionTemplateId = 'MISSION_AUTO_TRANSFER_SMALL'

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
    return `${error.status} ${error.code}: ${fields || error.message}`
  }
  if (error instanceof Error) {
    return error.message
  }
  return '알 수 없는 오류가 발생했어요.'
}

function formatKrw(value: number): string {
  return `${value.toLocaleString('ko-KR')}원`
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

  return (
    <div className="app-shell">
      <Header navigate={navigate} />
      <main className="app-main">{renderRoute(route, navigate)}</main>
    </div>
  )
}

function Header({ navigate }: { navigate: Navigate }) {
  const session = getSession()

  return (
    <header className="topbar">
      <button className="brand-button" type="button" onClick={() => navigate('/home')}>
        <span className="brand-mark">F</span>
        <span>
          <strong>FinMate</strong>
          <small>P0 Demo</small>
        </span>
      </button>
      <nav className="nav-actions" aria-label="P0 demo routes">
        <button type="button" onClick={() => navigate('/onboarding')}>
          Onboarding
        </button>
        <button type="button" onClick={() => navigate('/home')}>
          Home
        </button>
        <button type="button" onClick={() => navigate('/settings/privacy')}>
          Privacy
        </button>
      </nav>
      <div className="runtime-status">
        <span>{API_BASE_URL}</span>
        <strong>{session.accessToken ? 'accessToken ready' : 'no session'}</strong>
      </div>
    </header>
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
          title="없는 화면입니다"
          body="P0 데모 route만 열려 있어요."
          actionLabel="온보딩으로 이동"
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
        title="세션이 필요해요"
        body="ONB-02 흐름을 먼저 완료하면 accessToken이 저장됩니다."
        actionLabel="온보딩 시작"
        onAction={() => navigate('/onboarding')}
      />
    )
  }

  return render(session.accessToken)
}

function OnboardingPage({ navigate }: { navigate: Navigate }) {
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [steps, setSteps] = useState([
    { label: '30초 진단', status: 'pending', detail: 'P001 기준 고정 payload' },
    { label: 'mock 마이데이터 동의', status: 'pending', detail: 'MOCK_MYDATA 연결' },
    { label: '개인정보 동의', status: 'pending', detail: '공개 미리보기 생성' },
    { label: '데모 세션 시작', status: 'pending', detail: 'demo-token 발급' },
  ])

  const markStep = (index: number, status: string, detail: string) => {
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
      markStep(0, 'loading', '진단 생성 중')
      const diagnosis = await api.createDiagnosis()
      saveSession({
        diagnosisId: diagnosis.diagnosisId,
        onboardingToken: diagnosis.onboardingToken,
        goalType: diagnosis.goalType,
      })
      markStep(0, 'done', diagnosis.cohortLabel)

      markStep(1, 'loading', '동의 저장 중')
      const mockConsent = await api.createMockConsent(
        diagnosis.onboardingToken,
        diagnosis.diagnosisId,
      )
      saveSession({ mydataConnectionId: mockConsent.mydataConnectionId })
      markStep(1, 'done', `${mockConsent.status} · ${mockConsent.dataMode}`)

      markStep(2, 'loading', '공개 범위 저장 중')
      const privacy = await api.createPrivacyConsents(diagnosis.onboardingToken)
      saveSession({ privacySettingsId: privacy.privacySettingsId })
      markStep(2, 'done', `previewAvailable=${privacy.previewAvailable}`)

      markStep(3, 'loading', 'accessToken 발급 중')
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
      markStep(3, 'done', `${demoSession.userId} · ${demoSession.accessToken}`)
      navigate('/home')
    } catch (caught) {
      setError(describeError(caught))
    } finally {
      setRunning(false)
    }
  }

  return (
    <section className="page-grid">
      <div className="page-intro">
        <p className="screen-id">ONB-02</p>
        <h1>30초 진단에서 홈까지 한 번에 연결</h1>
        <p>
          고정 P0 입력으로 diagnosis, mock consent, privacy consent, demo session을
          순서대로 호출합니다.
        </p>
      </div>
      <div className="panel">
        <div className="step-list">
          {steps.map((step) => (
            <div className="step-row" key={step.label}>
              <span className={`status-dot ${step.status}`} />
              <div>
                <strong>{step.label}</strong>
                <small>{step.detail}</small>
              </div>
            </div>
          ))}
        </div>
        {error ? <InlineError message={error} /> : null}
        <div className="button-row">
          <button className="primary-button" type="button" onClick={startOnboarding} disabled={running}>
            {running ? '진행 중' : 'P0 데모 시작'}
          </button>
          <button type="button" onClick={() => navigate('/home')}>
            저장된 세션으로 홈 보기
          </button>
        </div>
      </div>
    </section>
  )
}

function HomePage({ token, navigate }: { token: string; navigate: Navigate }) {
  const loadHome = useCallback(() => api.getHome(token), [token])
  const state = useAsyncData<HomeResponse>(loadHome)

  return (
    <AsyncBoundary state={state}>
      {(home) => (
        <section className="stack">
          <div className="page-intro">
            <p className="screen-id">HOME-01</p>
            <h1>{home.goal.label}</h1>
            <p>{home.peerTeaser.mainDifference}</p>
          </div>
          <div className="metric-grid">
            <Metric label="오늘 예산" value={formatKrw(home.todayBudget)} />
            <Metric label="이번 달 소비" value={formatKrw(home.spendingSummary.monthlySpent)} />
            <Metric label="현금성 자산" value={formatKrw(home.assetSummary.cashLikeAssets)} />
            <Metric
              label="비상금 준비율"
              value={formatMonths(home.assetSummary.emergencyFundMonths)}
            />
          </div>
          <div className="split-layout">
            <article className="panel">
              <p className="eyebrow">todayMissionCandidate</p>
              <h2>{home.todayMissionCandidate.title}</h2>
              <p>추천 방식: {home.todayMissionCandidate.recommendationSource}</p>
            </article>
            <article className="panel accent-panel">
              <p className="eyebrow">peerTeaser</p>
              <h2>{home.peerTeaser.title}</h2>
              <p>유사도 {formatRatio(home.peerTeaser.similarityScore)}</p>
              <p>{home.peerTeaser.mainDifference}</p>
              <button
                className="primary-button"
                type="button"
                onClick={() => navigate(`/explore/portfolios/${home.peerTeaser.portfolioId}`)}
              >
                또래 사례 보기
              </button>
            </article>
          </div>
        </section>
      )}
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
          <div className="page-intro">
            <p className="screen-id">EXP-03</p>
            <h1>{portfolio.displayName}</h1>
            <p>
              {portfolio.dataMode} · {portfolio.visibility} · {portfolio.status}
            </p>
          </div>
          <div className="metric-grid">
            <Metric
              label="비상금 준비율"
              value={formatMonths(portfolio.financialSummary.emergencyFundMonths)}
            />
            <Metric label="저축률" value={formatRatio(portfolio.financialSummary.savingsRate)} />
            <Metric
              label="고정비 비중"
              value={formatRatio(portfolio.financialSummary.fixedCostRatio)}
            />
          </div>
          <div className="panel">
            <p className="eyebrow">routineCards</p>
            <div className="routine-list">
              {portfolio.routineCards.map((card) => (
                <article className="routine-row" key={card.title}>
                  <strong>{card.title}</strong>
                  <span>{card.description}</span>
                </article>
              ))}
            </div>
            <div className="chip-row">
              {portfolio.privacyBadges.map((badge) => (
                <span className="chip" key={badge}>
                  {badge}
                </span>
              ))}
            </div>
            <button
              className="primary-button"
              type="button"
              onClick={() => navigate(`/explore/compare/${portfolio.portfolioId}`)}
            >
              비교하기
            </button>
          </div>
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
      {(comparison) => (
        <section className="stack">
          <div className="page-intro">
            <p className="screen-id">EXP-04</p>
            <h1>{comparison.mainGap.label}</h1>
            <p>
              mainGap.normalizedGap {comparison.mainGap.normalizedGap.toFixed(2)} ·
              similarityScore {comparison.similarityScore.toFixed(2)}
            </p>
          </div>
          <div className="panel">
            <p className="eyebrow">gapItems</p>
            <div className="table-list">
              {comparison.gapItems.map((item) => (
                <div className="table-row" key={item.type}>
                  <strong>{item.type}</strong>
                  <span>나 {formatGapValue(item.userValue, item.unit)}</span>
                  <span>또래 {formatGapValue(item.peerValue, item.unit)}</span>
                </div>
              ))}
            </div>
            <p className="hint">
              리스트는 gapItems를 그대로 표시하고, 강조 수치는 mainGap.normalizedGap만
              사용합니다.
            </p>
            <button
              className="primary-button"
              type="button"
              onClick={() => navigate(`/simulations/${comparison.comparisonId}`)}
            >
              {comparison.nextAction.label}
            </button>
          </div>
        </section>
      )}
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
          <div className="page-intro">
            <p className="screen-id">SIM-01</p>
            <h1>3개월 뒤 변화</h1>
            <p>{simulation.insight}</p>
          </div>
          <div className="split-layout">
            <ValueCard title="Before" values={simulation.before} />
            <ValueCard title="After" values={simulation.after} />
          </div>
          <div className="panel">
            <p>
              매월 추가 저축 {formatKrw(simulation.monthlyAdditionalSaving)} · 기간{' '}
              {simulation.periodMonths}개월
            </p>
            <p className="hint">{simulation.disclaimer}</p>
            <button
              className="primary-button"
              type="button"
              onClick={() => navigate(`/missions/new/${simulation.simulationId}`)}
            >
              {simulation.nextAction.label}
            </button>
          </div>
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
          <div className="page-intro">
            <p className="screen-id">MIS-01</p>
            <h1>{mission.title}</h1>
            <p>{mission.description}</p>
          </div>
          <div className="metric-grid">
            <Metric label="난이도" value={mission.difficulty} />
            <Metric label="검증" value={mission.verificationType} />
            <Metric label="포인트" value={`${mission.rewardPoints}P`} />
            <Metric label="상태" value={mission.status} />
          </div>
          <div className="panel">
            <p className="eyebrow">privacySharePreview</p>
            <h2>{mission.privacySharePreview.shareableText}</h2>
            <p>금액 포함 여부: {String(mission.privacySharePreview.containsAmount)}</p>
            <div className="button-row">
              <button type="button" onClick={() => navigate('/home')}>
                홈으로 이동
              </button>
              <button
                className="primary-button"
                type="button"
                onClick={() => navigate('/settings/privacy')}
              >
                개인정보 설정
              </button>
            </div>
          </div>
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
      const updated = await api.updatePrivacySettings(token)
      setActionMessage(`저장됨: friendShareDefault=${updated.friendShareDefault}`)
      setRefreshKey((key) => key + 1)
    } catch (caught) {
      setActionMessage(describeError(caught))
    }
  }

  const withdraw = async (ownPortfolioId: string) => {
    setActionMessage(null)
    setVerification([])
    try {
      const response = await api.withdrawAnonymousPortfolio(token, ownPortfolioId)
      const own = await verifyPortfolio(token, ownPortfolioId)
      const peer = await verifyPortfolio(token, 'peer-portfolio-023')
      setActionMessage(
        `${response.status}: ${response.affectedPortfolioIds.join(', ') || 'affected none'}`,
      )
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
          <div className="page-intro">
            <p className="screen-id">SET-01</p>
            <h1>{settings.preview.displayName}</h1>
            <p>
              {settings.consentVersion} · updatedAt {settings.updatedAt}
            </p>
          </div>
          <div className="split-layout">
            <article className="panel">
              <p className="eyebrow">exposedFields</p>
              <div className="chip-row">
                {settings.exposedFields.map((field) => (
                  <span className="chip" key={field}>
                    {field}
                  </span>
                ))}
              </div>
              <p className="hint">
                숨김 필드: {settings.preview.hiddenFields.join(', ') || '없음'}
              </p>
              <button type="button" onClick={saveMissionOnly}>
                친구 공유 기본값 MISSION_ONLY로 저장
              </button>
            </article>
            <article className="panel danger-panel">
              <p className="eyebrow">withdraw</p>
              <h2>{settings.ownPortfolioId}</h2>
              <p>익명 포트폴리오 공개 철회 후 own 조회는 410, peer 조회는 200이어야 합니다.</p>
              <button
                className="danger-button"
                type="button"
                onClick={() => withdraw(settings.ownPortfolioId)}
              >
                공개 동의 철회
              </button>
            </article>
          </div>
          {actionMessage ? <p className="toast">{actionMessage}</p> : null}
          {verification.length > 0 ? (
            <div className="panel">
              <p className="eyebrow">privacy verification</p>
              <div className="table-list">
                {verification.map((item) => (
                  <div className={`table-row ${item.tone}`} key={item.portfolioId}>
                    <strong>{item.portfolioId}</strong>
                    <span>{item.status}</span>
                    <span>{item.detail}</span>
                  </div>
                ))}
              </div>
            </div>
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
    return <EmptyState title="불러오는 중" body="mock API 응답을 기다리고 있어요." />
  }
  if (state.status === 'error') {
    return <EmptyState title="API 오류" body={state.message} />
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

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <article className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  )
}

function ValueCard({ title, values }: { title: string; values: SimulationResponse['before'] }) {
  return (
    <article className="panel">
      <p className="eyebrow">{title}</p>
      <h2>{formatMonths(values.emergencyFundMonths)}</h2>
      <p>{formatKrw(values.cashLikeAssets)}</p>
    </article>
  )
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

export default App
