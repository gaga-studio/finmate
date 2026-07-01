import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, api } from './api'
import { clearSession, getSession, saveSession, type FinMateSession } from './session'
import type { AppAction, AppItem, AppMetric, AppScreenResponse, AppSection } from './types'
import './App.css'

type Navigate = (path: string) => void
type TabKey = 'home' | 'compare' | 'mission' | 'records' | 'profile'

type Route =
  | { name: 'login' }
  | { name: 'signup' }
  | { name: 'onboarding' }
  | { name: 'screen'; screen: ScreenKey; param?: string }
  | { name: 'birthday-contribution'; fundId: string }
  | { name: 'mission-feedback'; missionId: string }
  | { name: 'not-found' }

type ScreenKey =
  | 'home'
  | 'home-detail'
  | 'compare'
  | 'compare-filter'
  | 'compare-results'
  | 'compare-result'
  | 'compare-coach'
  | 'missions'
  | 'mission-detail'
  | 'records'
  | 'record-detail'
  | 'profile'
  | 'profile-section'
  | 'birthdays'
  | 'birthday-flow'
  | 'birthday-complete'
  | 'birthday-open'
  | 'birthday-share'
  | 'birthday-status'

type LoadState =
  | { status: 'loading' }
  | { status: 'success'; screen: AppScreenResponse }
  | { status: 'error'; message: string }

type StepStatus = 'pending' | 'loading' | 'done'

type OnboardingStep = {
  label: string
  status: StepStatus
  detail: string
}

const tabItems: Array<{ key: TabKey; label: string; icon: IconName; path: string }> = [
  { key: 'home', label: '홈', icon: 'home', path: '/home' },
  { key: 'compare', label: '비교', icon: 'search', path: '/compare' },
  { key: 'mission', label: '미션', icon: 'check-square', path: '/missions' },
  { key: 'records', label: '기록', icon: 'calendar', path: '/records' },
  { key: 'profile', label: '프로필', icon: 'profile', path: '/profile' },
]

function parseRoute(pathname: string): Route {
  const parts = pathname.split('/').filter(Boolean)

  if (parts.length === 0) {
    return { name: 'login' }
  }
  if (parts[0] === 'login') {
    return { name: 'login' }
  }
  if (parts[0] === 'signup') {
    return { name: 'signup' }
  }
  if (parts[0] === 'onboarding') {
    return { name: 'onboarding' }
  }
  if (parts[0] === 'home') {
    return parts[1]
      ? { name: 'screen', screen: 'home-detail', param: parts[1] }
      : { name: 'screen', screen: 'home' }
  }
  if (parts[0] === 'compare') {
    if (parts[1] === 'filter' && parts[2] === 'results') {
      return { name: 'screen', screen: 'compare-results' }
    }
    if (parts[1] === 'filter') {
      return { name: 'screen', screen: 'compare-filter' }
    }
    if (parts[1] === 'result') {
      return { name: 'screen', screen: 'compare-result', param: 'cmp-001' }
    }
    if (parts[1] === 'coach') {
      return { name: 'screen', screen: 'compare-coach', param: 'cmp-001' }
    }
    return { name: 'screen', screen: 'compare' }
  }
  if (parts[0] === 'missions') {
    if (parts[1] === 'new') {
      return { name: 'screen', screen: 'missions' }
    }
    if (parts[1] && parts[2] === 'feedback') {
      return { name: 'mission-feedback', missionId: parts[1] }
    }
    return parts[1]
      ? { name: 'screen', screen: 'mission-detail', param: parts[1] }
      : { name: 'screen', screen: 'missions' }
  }
  if (parts[0] === 'records') {
    return parts[1]
      ? { name: 'screen', screen: 'record-detail', param: parts[1] }
      : { name: 'screen', screen: 'records' }
  }
  if (parts[0] === 'profile') {
    return parts[1]
      ? { name: 'screen', screen: 'profile-section', param: parts[1] }
      : { name: 'screen', screen: 'profile' }
  }
  if (parts[0] === 'settings' && parts[1] === 'privacy') {
    return { name: 'screen', screen: 'profile-section', param: 'privacy' }
  }
  if (parts[0] === 'birthdays') {
    return parts[1]
      ? { name: 'screen', screen: 'birthday-flow', param: parts[1] }
      : { name: 'screen', screen: 'birthdays' }
  }
  if (parts[0] === 'birthday-funds') {
    if (parts[1] === 'me') {
      if (parts[2] === 'open') {
        return { name: 'screen', screen: 'birthday-open' }
      }
      if (parts[2] === 'share') {
        return { name: 'screen', screen: 'birthday-share' }
      }
      if (parts[2] === 'status') {
        return { name: 'screen', screen: 'birthday-status' }
      }
    }
    if (parts[2] === 'contribute') {
      return { name: 'birthday-contribution', fundId: parts[1] }
    }
    if (parts[2] === 'complete') {
      return { name: 'screen', screen: 'birthday-complete', param: parts[1] }
    }
  }
  if (parts[0] === 'explore' && parts[1] === 'compare') {
    return { name: 'screen', screen: 'compare-result', param: 'cmp-001' }
  }
  if (parts[0] === 'explore' && parts[1] === 'portfolios') {
    return { name: 'screen', screen: 'compare-filter' }
  }
  if (parts[0] === 'simulations') {
    return { name: 'screen', screen: 'compare-coach', param: 'cmp-001' }
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

function App() {
  const [pathname, navigate] = usePathname()
  const [session, setSession] = useState<FinMateSession>(() => getSession())
  const [refreshing, setRefreshing] = useState(() => {
    const currentSession = getSession()
    return !currentSession.accessToken && currentSession.canRefresh === true
  })
  const route = useMemo(() => parseRoute(pathname), [pathname])
  const activeTab = getActiveTab(route)

  useEffect(() => {
    const handleSessionChange = () => setSession(getSession())
    window.addEventListener('finmate-session-change', handleSessionChange)
    return () => window.removeEventListener('finmate-session-change', handleSessionChange)
  }, [])

  useEffect(() => {
    let active = true
    if (session.accessToken || session.canRefresh !== true) {
      setRefreshing(false)
      return () => {
        active = false
      }
    }
    api.refresh()
      .then((response) => {
        if (active) {
          saveSession({ accessToken: response.accessToken, expiresAt: response.expiresAt, user: response.user })
        }
      })
      .catch(() => {
        if (active) {
          clearSession()
        }
      })
      .finally(() => {
        if (active) {
          setRefreshing(false)
        }
      })
    return () => {
      active = false
    }
  }, [session.accessToken, session.canRefresh])

  const handleAuth = (next: FinMateSession, target: string) => {
    saveSession(next)
    setSession(getSession())
    navigate(target)
  }

  if (refreshing && route.name !== 'login' && route.name !== 'signup') {
    return (
      <div className="app-canvas">
        <div className="phone-shell">
          <main className="app-main"><LoadingScreen /></main>
        </div>
      </div>
    )
  }

  if (route.name === 'login') {
    return <AuthShell><AuthPage mode="login" onAuth={handleAuth} navigate={navigate} session={session} /></AuthShell>
  }
  if (route.name === 'signup') {
    return <AuthShell><AuthPage mode="signup" onAuth={handleAuth} navigate={navigate} session={session} /></AuthShell>
  }
  if (!session.accessToken) {
    return <AuthShell><AuthPage mode="login" onAuth={handleAuth} navigate={navigate} session={session} /></AuthShell>
  }

  return (
    <div className="app-canvas">
      <div className="phone-shell">
        <main className="app-main">{renderRoute(route, pathname, navigate, session)}</main>
        {route.name !== 'onboarding' ? <BottomNav active={activeTab} navigate={navigate} /> : null}
      </div>
    </div>
  )
}

function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="app-canvas">
      <div className="phone-shell">
        <main className="app-main">{children}</main>
      </div>
    </div>
  )
}

function renderRoute(route: Route, pathname: string, navigate: Navigate, session: FinMateSession): ReactNode {
  if (route.name === 'onboarding') {
    return <OnboardingPage navigate={navigate} session={session} />
  }
  if (route.name === 'birthday-contribution') {
    return <BirthdayContributionPage fundId={route.fundId} navigate={navigate} />
  }
  if (route.name === 'mission-feedback') {
    return <MissionFeedbackPage missionId={route.missionId} navigate={navigate} />
  }
  if (route.name === 'screen') {
    return <AppScreenPage pathname={pathname} route={route} navigate={navigate} />
  }
  return <NotFoundPage navigate={navigate} />
}

function getActiveTab(route: Route): TabKey {
  if (route.name === 'birthday-contribution') {
    return 'home'
  }
  if (route.name === 'mission-feedback') {
    return 'mission'
  }
  if (route.name !== 'screen') {
    return 'home'
  }
  if (route.screen.startsWith('compare')) {
    return 'compare'
  }
  if (route.screen.startsWith('mission')) {
    return 'mission'
  }
  if (route.screen.startsWith('record')) {
    return 'records'
  }
  if (route.screen.startsWith('profile')) {
    return 'profile'
  }
  return 'home'
}

function AuthPage({
  mode,
  onAuth,
  navigate,
  session,
}: {
  mode: 'login' | 'signup'
  onAuth: (session: FinMateSession, target: string) => void
  navigate: Navigate
  session: FinMateSession
}) {
  const [email, setEmail] = useState(mode === 'signup' ? '' : 'minjun@finmate.local')
  const [password, setPassword] = useState(mode === 'signup' ? '' : 'password123!')
  const [displayName, setDisplayName] = useState('민준')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isSignup = mode === 'signup'

  const submit = async () => {
    setBusy(true)
    setError(null)
    try {
      const response = isSignup
        ? await api.signup(email, password, displayName)
        : await api.login(email, password)
      onAuth(
        { accessToken: response.accessToken, expiresAt: response.expiresAt, user: response.user },
        response.user.onboardingCompleted ? '/home' : '/onboarding',
      )
    } catch (caught) {
      setError(describeError(caught))
    } finally {
      setBusy(false)
    }
  }

  if (session.accessToken && session.user) {
    return (
      <div className="screen auth-screen">
        <StatusBar time="9:41" />
        <section className="auth-hero">
          <img src="/assets/characters/finmate-main.png" alt="" />
          <h1>{session.user.displayName}님, 다시 시작할까요?</h1>
          <p>저장된 계정으로 금융 루틴 앱을 이어서 사용할 수 있어요.</p>
        </section>
        <button className="app-button primary" type="button" onClick={() => navigate(session.user?.onboardingCompleted ? '/home' : '/onboarding')}>앱으로 돌아가기</button>
        <button className="app-button secondary" type="button" onClick={() => { clearSession(); navigate('/login') }}>다른 계정으로 로그인</button>
      </div>
    )
  }

  return (
    <div className="screen auth-screen">
      <StatusBar time="9:41" />
      <section className="auth-hero">
        <img src="/assets/characters/finmate-main.png" alt="" />
        <h1>{isSignup ? 'FinMate 시작하기' : 'FinMate 로그인'}</h1>
        <p>계정으로 미션, 기록, 포인트, 친구 금융 생활을 안전하게 저장해요.</p>
      </section>
      <form className="auth-form" onSubmit={(event) => { event.preventDefault(); void submit() }}>
        {isSignup ? (
          <label>
            이름
            <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} autoComplete="name" />
          </label>
        ) : null}
        <label>
          이메일
          <input value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" inputMode="email" />
        </label>
        <label>
          비밀번호
          <input value={password} onChange={(event) => setPassword(event.target.value)} autoComplete={isSignup ? 'new-password' : 'current-password'} type="password" />
        </label>
        {error ? <p className="error-copy">{error}</p> : null}
        <button className="app-button primary" type="submit" disabled={busy}>
          {busy ? '처리 중' : isSignup ? '회원가입' : '로그인'}
        </button>
      </form>
      <button className="text-link" type="button" onClick={() => navigate(isSignup ? '/login' : '/signup')}>
        {isSignup ? '이미 계정이 있어요' : '처음이라면 회원가입'}
      </button>
    </div>
  )
}

function AppScreenPage({
  pathname,
  route,
  navigate,
}: {
  pathname: string
  route: Extract<Route, { name: 'screen' }>
  navigate: Navigate
}) {
  const state = useAppScreen(pathname, route)

  if (state.status === 'loading') {
    return <LoadingScreen />
  }
  if (state.status === 'error') {
    return <ErrorScreen message={state.message} navigate={navigate} />
  }
  return <ScreenRenderer screen={state.screen} navigate={navigate} />
}

function useAppScreen(pathname: string, route: Extract<Route, { name: 'screen' }>): LoadState {
  const [state, setState] = useState<LoadState>({ status: 'loading' })

  useEffect(() => {
    let active = true
    setState({ status: 'loading' })
    loadScreen(route)
      .then((screen) => {
        if (active) {
          setState({ status: 'success', screen })
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
  }, [pathname, route])

  return state
}

function loadScreen(route: Extract<Route, { name: 'screen' }>): Promise<AppScreenResponse> {
  switch (route.screen) {
    case 'home':
      return api.getAppHome()
    case 'home-detail':
      return api.getAppHomeDetail(route.param ?? 'mission')
    case 'compare':
      return api.getAppCompare()
    case 'compare-filter':
      return api.getAppCompareFilter()
    case 'compare-results':
      return api.searchAppCompareFilter()
    case 'compare-result':
      return api.getAppCompareResult(route.param ?? 'cmp-001')
    case 'compare-coach':
      return api.getAppCoachFlow(route.param ?? 'cmp-001')
    case 'missions':
      return api.getAppMissions()
    case 'mission-detail':
      return api.getAppMission(route.param ?? 'mission-food')
    case 'records':
      return api.getAppRecords()
    case 'record-detail':
      return api.getAppRecordDetail(route.param ?? '2026-06-12')
    case 'profile':
      return api.getAppProfile()
    case 'profile-section':
      return api.getAppProfileSection(route.param ?? 'followers')
    case 'birthdays':
      return api.getAppBirthdays()
    case 'birthday-flow':
      return api.getAppBirthdayFlow(route.param ?? 'bday-jiwoo')
    case 'birthday-complete':
      return api.getBirthdayContributionComplete(route.param ?? 'fund-jiwoo')
    case 'birthday-open':
      return api.getMyBirthdayFundOpenScreen()
    case 'birthday-share':
      return api.getMyBirthdayFundShareScreen()
    case 'birthday-status':
      return api.getMyBirthdayFundStatus()
  }
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'INVALID_CREDENTIALS') {
      return '이메일 또는 비밀번호를 다시 확인해주세요.'
    }
    if (error.code === 'UNAUTHORIZED') {
      return '로그인이 필요해요. 다시 로그인해주세요.'
    }
    if (error.code === 'MISSION_NOT_FOUND') {
      return '미션을 찾을 수 없어요. 홈에서 다시 선택해주세요.'
    }
    if (error.code === 'PORTFOLIO_NOT_AVAILABLE') {
      return '해당 사례 카드는 더 이상 공개되지 않아요.'
    }
    if (error.code === 'VALIDATION_ERROR') {
      return '입력한 내용을 다시 확인해주세요.'
    }
    if (error.status >= 500) {
      return '잠시 후 다시 시도해주세요.'
    }
    return '요청을 처리하지 못했어요.'
  }
  if (error instanceof Error) {
    return '네트워크 상태를 확인하고 다시 시도해주세요.'
  }
  return '화면을 불러오지 못했어요.'
}

function ScreenRenderer({ screen, navigate }: { screen: AppScreenResponse; navigate: Navigate }) {
  const canGoBack = !['home', 'compare', 'missions', 'records:2026-06', 'profile'].includes(screen.screenId)

  return (
    <div className="screen">
      <StatusBar time={screen.statusBarTime} />
      <header className="app-header">
        <div className="header-side">
          {canGoBack ? (
            <IconButton icon="back" label="뒤로" onClick={() => window.history.back()} />
          ) : null}
        </div>
        <h1>{screen.title}</h1>
        <div className="header-side right">
          <IconButton icon={headerIcon(screen)} label="메뉴" onClick={() => navigate(headerPath(screen))} />
        </div>
      </header>

      <section className="screen-stack">
        {screen.sections.map((section) => (
          <SectionRenderer section={section} navigate={navigate} key={section.id} />
        ))}
      </section>
    </div>
  )
}

function headerIcon(screen: AppScreenResponse): IconName {
  if (screen.tab === 'records') {
    return 'chart'
  }
  if (screen.tab === 'mission') {
    return 'gift'
  }
  if (screen.tab === 'profile') {
    return 'settings'
  }
  if (screen.tab === 'compare') {
    return 'sliders'
  }
  return 'bell'
}

function headerPath(screen: AppScreenResponse): string {
  if (screen.tab === 'records') {
    return '/records/stats'
  }
  if (screen.tab === 'mission') {
    return '/missions/next-goals'
  }
  if (screen.tab === 'profile') {
    return '/settings/privacy'
  }
  if (screen.tab === 'compare') {
    return '/compare/filter'
  }
  return '/birthdays'
}

function SectionRenderer({ section, navigate }: { section: AppSection; navigate: Navigate }) {
  if (section.kind === 'greeting' || section.kind === 'lead') {
    return <LeadSection section={section} />
  }
  if (section.kind === 'missionHero') {
    return <MissionHero section={section} navigate={navigate} />
  }
  if (section.kind === 'budget') {
    return <BudgetSection section={section} navigate={navigate} />
  }
  if (section.kind === 'spendingGrid' || section.kind === 'signalGrid' || section.kind === 'scoreGrid') {
    return <GridSection section={section} navigate={navigate} />
  }
  if (section.kind === 'asset') {
    return <AssetSection section={section} navigate={navigate} />
  }
  if (section.kind === 'compareBars' || section.kind === 'distribution') {
    return <CompareBarsSection section={section} navigate={navigate} />
  }
  if (section.kind === 'calendar') {
    return <CalendarSection section={section} navigate={navigate} />
  }
  if (section.kind === 'coach' || section.kind === 'birthday') {
    return <IllustratedSection section={section} navigate={navigate} />
  }
  if (section.kind === 'points' || section.kind === 'profileHero' || section.kind === 'actionCard') {
    return <MetricCardSection section={section} navigate={navigate} />
  }
  if (section.kind === 'chipGroup') {
    return <ChipSection section={section} />
  }
  return <ListSection section={section} navigate={navigate} />
}

function LeadSection({ section }: { section: AppSection }) {
  return (
    <div className={section.kind === 'greeting' ? 'greeting-block' : 'lead-block'}>
      <h1>{section.title}</h1>
      {section.subtitle ? <p>{section.subtitle}</p> : null}
    </div>
  )
}

function MissionHero({ section, navigate }: SectionProps) {
  const metric = section.metrics?.[0]
  return (
    <>
      <button className="card hero-card mission-hero-card" type="button" onClick={() => goDetail(section, navigate)}>
        <div className="hero-copy">
          <span>{section.subtitle}</span>
          <strong>{section.title}</strong>
          {metric ? <small>{metric.label} {metric.value}</small> : null}
          <ProgressLine value={metric?.progress ?? 0} tone="purple" />
        </div>
        {metric ? <RingProgress value={metric.progress ?? 0} label={metric.caption ?? '진행 중'} /> : <Chevron />}
      </button>
      <ActionButtons actions={section.actions} navigate={navigate} />
    </>
  )
}

function BudgetSection({ section, navigate }: SectionProps) {
  const progress = numberFromData(section.data, 'progress') ?? section.metrics?.[1]?.progress ?? 0
  return (
    <Card section={section} navigate={navigate}>
      <div className="metric-row">
        {section.metrics?.map((metric) => (
          <MetricView metric={metric} key={metric.label} />
        ))}
      </div>
      <ProgressLine value={progress} tone="green" />
    </Card>
  )
}

function GridSection({ section, navigate }: SectionProps) {
  const isScore = section.kind === 'scoreGrid'
  return (
    <Card section={section} navigate={navigate}>
      {isScore ? (
        <div className="score-grid">
          {section.metrics?.map((metric) => <ScoreTile metric={metric} key={metric.label} />)}
        </div>
      ) : (
        <div className="tile-grid">
          {section.items?.map((item) => <Tile item={item} navigate={navigate} key={item.id} />)}
        </div>
      )}
    </Card>
  )
}

function AssetSection({ section, navigate }: SectionProps) {
  return (
    <Card section={section} navigate={navigate}>
      <div className="asset-layout">
        <div>
          {section.metrics?.map((metric) => (
            <MetricView metric={metric} key={metric.label} />
          ))}
        </div>
        <MiniLineChart values={arrayFromData(section.data, 'sparkline')} />
      </div>
    </Card>
  )
}

function CompareBarsSection({ section, navigate }: SectionProps) {
  return (
    <Card section={section} navigate={navigate}>
      <div className="bar-list">
        {section.items?.map((item) => (
          <BarRow item={item} navigate={navigate} key={item.id} />
        ))}
      </div>
      <ActionButtons actions={section.actions} navigate={navigate} />
    </Card>
  )
}

function CalendarSection({ section, navigate }: SectionProps) {
  const itemByDay = new Map((section.items ?? []).map((item) => [Number(item.title), item]))
  const days = Array.from({ length: 30 }, (_, index) => itemByDay.get(index + 1) ?? {
    id: `2026-06-${String(index + 1).padStart(2, '0')}`,
    title: String(index + 1),
    tone: 'empty',
  })

  return (
    <div className="calendar-block">
      <div className="month-heading">
        <Chevron direction="left" />
        <h1>{section.title}</h1>
        <Chevron />
      </div>
      <div className="weekdays">
        {['일', '월', '화', '수', '목', '금', '토'].map((day) => <span key={day}>{day}</span>)}
      </div>
      <div className="calendar-grid">
        {days.map((item) => (
          <button
            className={`calendar-cell ${item.tone ?? 'empty'}`}
            type="button"
            onClick={() => item.detailPath && navigate(item.detailPath)}
            key={item.id}
          >
            <strong>{item.title}</strong>
            {item.value ? <small>{item.value}</small> : null}
            <i />
          </button>
        ))}
      </div>
      <div className="legend-row">
        <Legend tone="success" label="미션 성공" />
        <Legend tone="over" label="예산 초과" />
        <Legend tone="none" label="기록 없음" />
      </div>
      <ActionButtons actions={section.actions} navigate={navigate} />
    </div>
  )
}

function IllustratedSection({ section, navigate }: SectionProps) {
  const asset = section.heroAsset
  return (
    <Card section={section} navigate={navigate} className={`illustrated-card ${section.kind}`}>
      <div className="illustrated-layout">
        <div>
          {section.subtitle ? <p>{section.subtitle}</p> : null}
          <div className="metric-row compact">
            {section.metrics?.map((metric) => (
              <MetricView metric={metric} key={metric.label} />
            ))}
          </div>
        </div>
        {asset ? <img className="character-art" src={asset} alt="" /> : null}
      </div>
      {section.metrics?.[0]?.progress ? (
        <ProgressLine value={section.metrics[0].progress ?? 0} tone="green" />
      ) : null}
      <ActionButtons actions={section.actions} navigate={navigate} />
    </Card>
  )
}

function MetricCardSection({ section, navigate }: SectionProps) {
  return (
    <Card section={section} navigate={navigate} className={section.kind === 'points' ? 'points-card' : undefined}>
      <div className="metric-row">
        {section.metrics?.map((metric) => (
          <MetricView metric={metric} key={metric.label} />
        ))}
      </div>
      <ActionButtons actions={section.actions} navigate={navigate} />
    </Card>
  )
}

function ChipSection({ section }: { section: AppSection }) {
  return (
    <article className="card">
      <h2>{section.title}</h2>
      <div className="chip-row">
        {section.items?.map((item) => (
          <span className={`chip ${item.tone ?? 'muted'}`} key={item.id}>{item.title}</span>
        ))}
      </div>
    </article>
  )
}

function ListSection({ section, navigate }: SectionProps) {
  return (
    <Card section={section} navigate={navigate}>
      <div className={section.kind === 'profileRail' ? 'profile-rail' : 'list-stack'}>
        {section.items?.map((item, index) => (
          <ListItem item={item} index={index} navigate={navigate} rank={section.kind === 'rankList'} key={item.id} />
        ))}
      </div>
      <ActionButtons actions={section.actions} navigate={navigate} />
    </Card>
  )
}

function Card({
  section,
  navigate,
  children,
  className,
}: {
  section: AppSection
  navigate: Navigate
  children: ReactNode
  className?: string
}) {
  return (
    <article className={`card ${className ?? ''}`}>
      <SectionHeader section={section} navigate={navigate} />
      {section.subtitle && !['coach', 'birthday'].includes(section.kind) ? <p className="card-subtitle">{section.subtitle}</p> : null}
      {children}
    </article>
  )
}

function SectionHeader({ section, navigate }: { section: AppSection; navigate: Navigate }) {
  return (
    <div className="section-header">
      <h2>{section.title}</h2>
      {section.detailPath ? (
        <button type="button" onClick={() => navigate(section.detailPath ?? '/home')}>
          자세히 보기 <Chevron />
        </button>
      ) : null}
    </div>
  )
}

function MetricView({ metric }: { metric: AppMetric }) {
  return (
    <div className={`metric ${metric.tone ?? 'default'}`}>
      <span>{metric.label}</span>
      <strong>{metric.value}</strong>
      {metric.caption ? <small>{metric.caption}</small> : null}
    </div>
  )
}

function ScoreTile({ metric }: { metric: AppMetric }) {
  return (
    <div className={`score-tile ${metric.tone ?? 'purple'}`}>
      <span>{metric.label}</span>
      <strong>{metric.value}</strong>
      {metric.caption ? <small>{metric.caption}</small> : null}
    </div>
  )
}

function Tile({ item, navigate }: { item: AppItem; navigate: Navigate }) {
  return (
    <button className="mini-tile" type="button" onClick={() => item.detailPath && navigate(item.detailPath)}>
      <IconBadge icon={item.icon ?? 'more'} tone={item.tone ?? 'purple'} />
      <strong>{item.title}</strong>
      {item.value ? <b>{item.value}</b> : null}
      {item.caption ? <small>{item.caption}</small> : null}
    </button>
  )
}

function BarRow({ item, navigate }: { item: AppItem; navigate: Navigate }) {
  const mine = numberFromData(item.data, 'mine') ?? numberFromData(item.data, 'progress') ?? 50
  const group = numberFromData(item.data, 'group') ?? 0

  return (
    <button className="bar-row" type="button" onClick={() => item.detailPath && navigate(item.detailPath)}>
      <IconBadge icon={item.icon ?? 'saving'} tone={item.tone ?? 'purple'} />
      <div className="bar-copy">
        <strong>{item.title}</strong>
        {item.subtitle ? <span>{item.subtitle}</span> : null}
        <div className="dual-bars">
          <ProgressLine value={mine} tone="purple" />
          {group ? <ProgressLine value={group} tone="gray" /> : null}
        </div>
      </div>
      <div className="bar-value">
        {item.value ? <b>{item.value}</b> : null}
        {item.caption ? <small>{item.caption}</small> : null}
      </div>
    </button>
  )
}

function ListItem({
  item,
  index,
  rank,
  navigate,
}: {
  item: AppItem
  index: number
  rank: boolean
  navigate: Navigate
}) {
  return (
    <button className="list-item" type="button" onClick={() => item.detailPath && navigate(item.detailPath)}>
      {rank ? <span className="rank-dot">{index + 1}</span> : <IconBadge icon={item.icon ?? 'check'} tone={item.tone ?? 'purple'} />}
      <div>
        <strong>{item.title}</strong>
        {item.subtitle ? <small>{item.subtitle}</small> : null}
      </div>
      {item.value ? <b>{item.value}</b> : null}
      {item.caption ? <em>{item.caption}</em> : null}
      {item.detailPath ? <Chevron /> : null}
    </button>
  )
}

function ActionButtons({ actions, navigate }: { actions?: AppAction[] | null; navigate: Navigate }) {
  if (!actions?.length) {
    return null
  }
  return (
    <div className="action-row">
      {actions.map((action) => (
        <ActionButton action={action} navigate={navigate} key={`${action.label}-${action.path}`} />
      ))}
    </div>
  )
}

function ActionButton({ action, navigate }: { action: AppAction; navigate: Navigate }) {
  const [busy, setBusy] = useState(false)

  const handleClick = async () => {
    if (action.method === 'GET') {
      navigate(action.path)
      return
    }
    setBusy(true)
    try {
      if (action.intent === 'birthday-open') {
        await api.openMyBirthdayFund()
        navigate('/birthday-funds/me/status')
      } else if (action.intent === 'birthday-share') {
        await api.shareMyBirthdayFund()
        navigate('/birthday-funds/me/status')
      } else if (action.intent === 'mission-feedback') {
        navigate(action.path)
      } else if (action.intent === 'logout') {
        await api.logout()
        clearSession()
        navigate('/login')
      } else {
        navigate(action.path)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <button className={`app-button ${action.tone}`} type="button" disabled={busy} onClick={handleClick}>
      {busy ? '처리 중' : action.label}
    </button>
  )
}

function BirthdayContributionPage({ fundId, navigate }: { fundId: string; navigate: Navigate }) {
  const [amount, setAmount] = useState(10000)
  const [message, setMessage] = useState('지우야 생일 축하해!')
  const [anonymous, setAnonymous] = useState(false)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setBusy(true)
    try {
      await api.contributeBirthdayFund(fundId, { amount, message, anonymous })
      navigate(`/birthday-funds/${fundId}/complete`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="screen">
      <StatusBar time="9:41" />
      <header className="app-header">
        <div className="header-side"><IconButton icon="back" label="뒤로" onClick={() => navigate('/birthdays/bday-jiwoo')} /></div>
        <h1>참여하기</h1>
        <div className="header-side right"><IconButton icon="bell" label="알림" /></div>
      </header>
      <section className="screen-stack">
        <article className="card form-card">
          <h2>참여할 금액을 입력해주세요</h2>
          <div className="amount-display">₩{amount.toLocaleString('ko-KR')}</div>
          <div className="amount-options">
            {[5000, 10000, 20000].map((value) => (
              <button className={value === amount ? 'selected' : ''} type="button" onClick={() => setAmount(value)} key={value}>
                +{value.toLocaleString('ko-KR')}
              </button>
            ))}
          </div>
          <label className="field-label" htmlFor="birthday-message">축하 메시지</label>
          <textarea id="birthday-message" value={message} onChange={(event) => setMessage(event.target.value)} />
          <label className="toggle-row">
            <span>익명으로 참여하기</span>
            <input type="checkbox" checked={anonymous} onChange={(event) => setAnonymous(event.target.checked)} />
          </label>
          <button className="app-button primary" type="button" disabled={busy} onClick={submit}>
            {busy ? '참여 중' : '다음'}
          </button>
        </article>
      </section>
    </div>
  )
}

function MissionFeedbackPage({ missionId, navigate }: { missionId: string; navigate: Navigate }) {
  const [state, setState] = useState<LoadState>({ status: 'loading' })

  useEffect(() => {
    let active = true
    async function submit() {
      try {
        const result = await api.submitAppMissionFeedback(missionId)
        const screen: AppScreenResponse = {
          screenId: 'missions:feedback',
          title: '오늘 실천 피드백',
          tab: 'mission',
          statusBarTime: '9:41',
          heroAsset: '/assets/characters/finmate-growth.png',
          sections: [
            {
              id: 'feedback',
              kind: 'coach',
              title: result.title,
              subtitle: result.message,
              heroAsset: '/assets/characters/finmate-growth.png',
              metrics: [
                {
                  label: '오늘의 포인트',
                  value: `+${String(result.data.rewardPoints ?? 0)}P`,
                  caption: '포인트 지갑에 저장됨',
                  tone: 'purple',
                  progress: 100,
                },
              ],
              actions: [
                { label: '기록 완료', path: '/records/history', method: 'GET', tone: 'primary' },
                { label: '다음 목표 보기', path: '/missions/next-goals', method: 'GET', tone: 'secondary' },
              ],
            },
          ],
          meta: result.data,
        }
        if (active) {
          setState({ status: 'success', screen })
        }
      } catch (error) {
        if (active) {
          setState({ status: 'error', message: describeError(error) })
        }
      }
    }
    void submit()
    return () => {
      active = false
    }
  }, [missionId])

  if (state.status === 'loading') {
    return <LoadingScreen />
  }
  if (state.status === 'error') {
    return <ErrorScreen message={state.message} navigate={navigate} />
  }
  return <ScreenRenderer screen={state.screen} navigate={navigate} />
}

function LoadingScreen() {
  return (
    <div className="screen center-screen">
      <StatusBar time="9:41" />
      <div className="loader" />
      <p>화면을 불러오고 있어요</p>
    </div>
  )
}

function ErrorScreen({ message, navigate }: { message: string; navigate: Navigate }) {
  return (
    <div className="screen center-screen">
      <StatusBar time="9:41" />
      <h1>화면을 불러오지 못했어요</h1>
      <p>{message}</p>
      <button className="app-button primary" type="button" onClick={() => navigate('/home')}>홈으로</button>
    </div>
  )
}

function NotFoundPage({ navigate }: { navigate: Navigate }) {
  return (
    <div className="screen center-screen">
      <StatusBar time="9:41" />
      <h1>없는 화면이에요</h1>
      <button className="app-button primary" type="button" onClick={() => navigate('/home')}>홈으로</button>
    </div>
  )
}

function OnboardingPage({ navigate, session }: { navigate: Navigate; session: FinMateSession }) {
  const [steps, setSteps] = useState<OnboardingStep[]>([
    { label: '목표 설정', status: 'pending', detail: '비상금 1개월 만들기' },
    { label: '생활 기준 저장', status: 'pending', detail: '1인가구 · 안정 추구형' },
    { label: '기본 금융 루틴 준비', status: 'pending', detail: '미션, 기록, 포인트 지갑 생성' },
    { label: '앱 시작', status: 'pending', detail: '5탭 경험으로 이동' },
  ])
  const [error, setError] = useState<string | null>(null)

  const updateStep = (index: number, status: StepStatus) => {
    setSteps((current) => current.map((step, itemIndex) => itemIndex === index ? { ...step, status } : step))
  }

  const start = async () => {
    setError(null)
    try {
      updateStep(0, 'loading')
      await new Promise((resolve) => window.setTimeout(resolve, 180))
      updateStep(0, 'done')
      updateStep(1, 'loading')
      await new Promise((resolve) => window.setTimeout(resolve, 180))
      updateStep(1, 'done')
      updateStep(2, 'loading')
      const user = await api.completeOnboarding()
      updateStep(2, 'done')
      updateStep(3, 'loading')
      saveSession({ user })
      updateStep(3, 'done')
      navigate('/home')
    } catch (caught) {
      setError(describeError(caught))
    }
  }

  return (
    <div className="screen onboarding-screen">
      <StatusBar time="9:41" />
      <section className="onboarding-hero">
        <img src="/assets/characters/finmate-main.png" alt="" />
        <h1>{session.user?.displayName ?? 'FinMate'}님의 금융 루틴을 준비할게요</h1>
        <p>미션, 기록, 포인트 지갑, 친구 피드를 계정에 저장해 실제 앱처럼 이어서 사용할 수 있어요.</p>
      </section>
      <div className="onboarding-steps">
        {steps.map((step) => (
          <div className={`step-row ${step.status}`} key={step.label}>
            <IconBadge icon={step.status === 'done' ? 'check' : 'spark'} tone={step.status === 'done' ? 'green' : 'purple'} />
            <div>
              <strong>{step.label}</strong>
              <span>{step.detail}</span>
            </div>
          </div>
        ))}
      </div>
      {error ? <p className="error-copy">{error}</p> : null}
      <button className="app-button primary" type="button" onClick={start}>시작하기</button>
    </div>
  )
}

function StatusBar({ time }: { time: string }) {
  return (
    <div className="status-bar">
      <strong>{time}</strong>
      <span>
        <i />
        <i />
        <i />
      </span>
    </div>
  )
}

function BottomNav({ active, navigate }: { active: TabKey; navigate: Navigate }) {
  return (
    <nav className="bottom-nav">
      {tabItems.map((item) => (
        <button
          className={item.key === active ? 'active' : ''}
          type="button"
          onClick={() => navigate(item.path)}
          key={item.key}
        >
          <AppIcon name={item.icon} />
          <span>{item.label}</span>
        </button>
      ))}
    </nav>
  )
}

function IconButton({ icon, label, onClick }: { icon: IconName; label: string; onClick?: () => void }) {
  return (
    <button className="icon-button" type="button" aria-label={label} onClick={onClick}>
      <AppIcon name={icon} />
    </button>
  )
}

function IconBadge({ icon, tone }: { icon: string; tone: string }) {
  return (
    <span className={`icon-badge ${tone}`}>
      <AppIcon name={toIconName(icon)} />
    </span>
  )
}

function RingProgress({ value, label }: { value: number; label: string }) {
  const deg = Math.max(0, Math.min(100, value)) * 3.6
  return (
    <div className="ring" style={{ background: `conic-gradient(var(--purple) ${deg}deg, #eceaf3 0deg)` }}>
      <div>
        <strong>{value}%</strong>
        <span>{label}</span>
      </div>
    </div>
  )
}

function ProgressLine({ value, tone }: { value: number; tone: 'purple' | 'green' | 'gray' }) {
  return (
    <span className={`progress-line ${tone}`}>
      <i style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
    </span>
  )
}

function MiniLineChart({ values }: { values: number[] }) {
  const points = useMemo(() => {
    const safeValues = values.length ? values : [18, 24, 32, 28, 40, 36, 48]
    const max = Math.max(...safeValues)
    const min = Math.min(...safeValues)
    return safeValues
      .map((value, index) => {
        const x = (index / Math.max(1, safeValues.length - 1)) * 120
        const y = 58 - ((value - min) / Math.max(1, max - min)) * 46
        return `${x},${y}`
      })
      .join(' ')
  }, [values])

  return (
    <svg className="mini-chart" viewBox="0 0 120 64" role="img" aria-label="자산 변화 그래프">
      <polyline points={points} fill="none" stroke="var(--purple)" strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />
    </svg>
  )
}

function Chevron({ direction = 'right' }: { direction?: 'right' | 'left' }) {
  return <AppIcon name={direction === 'left' ? 'chevron-left' : 'chevron-right'} />
}

function Legend({ tone, label }: { tone: string; label: string }) {
  return (
    <span className="legend">
      <i className={tone} />
      {label}
    </span>
  )
}

function goDetail(section: AppSection, navigate: Navigate) {
  if (section.detailPath) {
    navigate(section.detailPath)
  }
}

function numberFromData(data: Record<string, unknown> | null | undefined, key: string): number | null {
  const value = data?.[key]
  return typeof value === 'number' ? value : null
}

function arrayFromData(data: Record<string, unknown> | null | undefined, key: string): number[] {
  const value = data?.[key]
  return Array.isArray(value) ? value.filter((item): item is number => typeof item === 'number') : []
}

type SectionProps = {
  section: AppSection
  navigate: Navigate
}

type IconName =
  | 'home'
  | 'search'
  | 'check-square'
  | 'calendar'
  | 'profile'
  | 'bell'
  | 'back'
  | 'help'
  | 'gift'
  | 'chart'
  | 'settings'
  | 'sliders'
  | 'chevron-right'
  | 'chevron-left'
  | 'food'
  | 'transport'
  | 'cafe'
  | 'more'
  | 'stocks'
  | 'saving'
  | 'fund'
  | 'pension'
  | 'study'
  | 'spend'
  | 'debt'
  | 'cart'
  | 'check'
  | 'spark'

function toIconName(icon: string): IconName {
  const iconMap: Record<string, IconName> = {
    'check-square': 'check-square',
    'avatar-j': 'profile',
    'avatar-m': 'profile',
    'avatar-t': 'profile',
    piggy: 'saving',
  }
  const mapped = iconMap[icon] ?? icon
  const allowed: IconName[] = [
    'home', 'search', 'check-square', 'calendar', 'profile', 'bell', 'back', 'help', 'gift', 'chart',
    'settings', 'sliders', 'chevron-right', 'chevron-left', 'food', 'transport', 'cafe', 'more',
    'stocks', 'saving', 'fund', 'pension', 'study', 'spend', 'debt', 'cart', 'check', 'spark',
  ]
  return allowed.includes(mapped as IconName) ? (mapped as IconName) : 'more'
}

function AppIcon({ name }: { name: IconName }) {
  const common = {
    width: 22,
    height: 22,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  }

  switch (name) {
    case 'home':
      return <svg {...common}><path d="m3 11 9-8 9 8" /><path d="M5 10v10h14V10" /><path d="M10 20v-6h4v6" /></svg>
    case 'search':
      return <svg {...common}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
    case 'check-square':
      return <svg {...common}><rect x="5" y="4" width="14" height="16" rx="3" /><path d="m8.5 12 2.2 2.2 4.8-5" /></svg>
    case 'calendar':
      return <svg {...common}><rect x="4" y="5" width="16" height="15" rx="3" /><path d="M8 3v4M16 3v4M4 10h16" /></svg>
    case 'profile':
      return <svg {...common}><circle cx="12" cy="8" r="4" /><path d="M5 21a7 7 0 0 1 14 0" /></svg>
    case 'bell':
      return <svg {...common}><path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 7h18s-3 0-3-7" /><path d="M10 20a2 2 0 0 0 4 0" /></svg>
    case 'back':
      return <svg {...common}><path d="m15 18-6-6 6-6" /></svg>
    case 'help':
      return <svg {...common}><circle cx="12" cy="12" r="9" /><path d="M9.5 9a2.5 2.5 0 1 1 4 2c-.8.5-1.5 1.1-1.5 2" /><path d="M12 17h.01" /></svg>
    case 'gift':
      return <svg {...common}><rect x="4" y="9" width="16" height="11" rx="2" /><path d="M12 9v11M4 13h16" /><path d="M9 9c-2 0-3-1-3-2.2S7 5 8.2 6.3L12 9" /><path d="M15 9c2 0 3-1 3-2.2S17 5 15.8 6.3L12 9" /></svg>
    case 'chart':
      return <svg {...common}><path d="M4 19V5" /><path d="M4 19h16" /><path d="M8 16V9M12 16V5M16 16v-4" /></svg>
    case 'settings':
      return <svg {...common}><circle cx="12" cy="12" r="3" /><path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1a7 7 0 0 0-1.7-1L14.5 3h-5l-.3 3.1a7 7 0 0 0-1.7 1l-2.4-1-2 3.4 2 1.5a7 7 0 0 0 0 2l-2 1.5 2 3.4 2.4-1a7 7 0 0 0 1.7 1l.3 3.1h5l.3-3.1a7 7 0 0 0 1.7-1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1Z" /></svg>
    case 'sliders':
      return <svg {...common}><path d="M4 7h10M18 7h2M4 17h2M10 17h10" /><circle cx="16" cy="7" r="2" /><circle cx="8" cy="17" r="2" /></svg>
    case 'chevron-left':
      return <svg {...common}><path d="m15 18-6-6 6-6" /></svg>
    case 'chevron-right':
      return <svg {...common}><path d="m9 18 6-6-6-6" /></svg>
    case 'food':
      return <svg {...common}><path d="M6 3v8M9 3v8M6 7h3" /><path d="M7.5 11v10" /><path d="M17 3v18" /><path d="M14 3c3 2 3 6 0 8" /></svg>
    case 'transport':
      return <svg {...common}><rect x="5" y="4" width="14" height="13" rx="3" /><path d="M8 17v2M16 17v2M8 8h8M8 13h.01M16 13h.01" /></svg>
    case 'cafe':
      return <svg {...common}><path d="M4 8h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5Z" /><path d="M16 10h2a2 2 0 0 1 0 4h-2" /><path d="M6 4v1M10 4v1M14 4v1" /></svg>
    case 'stocks':
      return <svg {...common}><path d="M4 17 9 12l4 4 7-9" /><path d="M15 7h5v5" /></svg>
    case 'saving':
      return <svg {...common}><rect x="5" y="7" width="14" height="10" rx="2" /><path d="M9 11h6M12 7V5" /></svg>
    case 'fund':
      return <svg {...common}><path d="m12 3 8 4-8 4-8-4 8-4Z" /><path d="M4 11l8 4 8-4" /><path d="M4 15l8 4 8-4" /></svg>
    case 'pension':
      return <svg {...common}><path d="M6 20V8l6-4 6 4v12" /><path d="M9 20v-7h6v7" /></svg>
    case 'study':
      return <svg {...common}><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5z" /></svg>
    case 'spend':
      return <svg {...common}><path d="M4 10h16M7 15h.01M11 15h2" /><rect x="3" y="6" width="18" height="12" rx="3" /></svg>
    case 'debt':
      return <svg {...common}><rect x="5" y="4" width="14" height="16" rx="3" /><path d="M9 9h6M9 13h6M9 17h3" /></svg>
    case 'cart':
      return <svg {...common}><circle cx="9" cy="20" r="1" /><circle cx="17" cy="20" r="1" /><path d="M3 4h2l2 12h11l2-8H7" /></svg>
    case 'check':
      return <svg {...common}><path d="m5 13 4 4L19 7" /></svg>
    case 'spark':
      return <svg {...common}><path d="m12 3 1.6 5.4L19 10l-5.4 1.6L12 17l-1.6-5.4L5 10l5.4-1.6L12 3Z" /></svg>
    case 'more':
      return <svg {...common}><circle cx="5" cy="12" r="1" /><circle cx="12" cy="12" r="1" /><circle cx="19" cy="12" r="1" /></svg>
  }
}

export default App
