import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, api } from './api'
import { clearSession, saveSession } from './session'
import {
  compareDemo,
  filterDemo,
  homeDemo,
  missionDemo,
  profileDemo,
  recordDemo,
  type CalendarDay,
  type CompareMetric,
  type FinanceSignal,
  type FilterProfile,
  type MissionItem,
  type SpendingCategory,
  type TabKey,
} from './demoData'
import './App.css'

type Navigate = (path: string) => void

type Route =
  | { name: 'onboarding' }
  | { name: 'home' }
  | { name: 'compare' }
  | { name: 'compare-filter' }
  | { name: 'mission' }
  | { name: 'records' }
  | { name: 'profile' }
  | { name: 'privacy' }
  | { name: 'not-found' }

type StepStatus = 'pending' | 'loading' | 'done'

type OnboardingStep = {
  label: string
  status: StepStatus
  detail: string
}

const onboardingStatuses = [
  { label: '학생/알바', detail: '이번 P0 반영', available: true },
  { label: '사회초년생', detail: '다음 버전', available: false },
  { label: '취준생', detail: '다음 버전', available: false },
  { label: '프리랜서', detail: '다음 버전', available: false },
]

const onboardingGoals = [
  { label: '비상금 만들기', detail: '이번 P0 반영', available: true },
  { label: '지출 줄이기', detail: '다음 버전', available: false },
  { label: '투자 시작 준비', detail: '다음 버전', available: false },
]

const privacyVisible = ['연령대', '소득 구간', '금융 목표', '금융 요약', '루틴 카드']
const privacyHidden = ['이름', '계좌번호', '거래처', '카드번호', '정확한 거래 시간']

function parseRoute(pathname: string): Route {
  const parts = pathname.split('/').filter(Boolean)

  if (parts.length === 0 || parts[0] === 'onboarding') {
    return { name: 'onboarding' }
  }
  if (parts[0] === 'home') {
    return { name: 'home' }
  }
  if (parts[0] === 'compare' && parts[1] === 'filter') {
    return { name: 'compare-filter' }
  }
  if (parts[0] === 'compare') {
    return { name: 'compare' }
  }
  if (parts[0] === 'missions') {
    return { name: 'mission' }
  }
  if (parts[0] === 'records') {
    return { name: 'records' }
  }
  if (parts[0] === 'profile') {
    return { name: 'profile' }
  }
  if (parts[0] === 'settings' && parts[1] === 'privacy') {
    return { name: 'privacy' }
  }
  if (parts[0] === 'explore' && (parts[1] === 'compare' || parts[1] === 'portfolios')) {
    return { name: 'compare' }
  }
  if (parts[0] === 'simulations') {
    return { name: 'mission' }
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
  const route = parseRoute(pathname)
  const activeTab = getActiveTab(route)

  return (
    <div className="app-canvas">
      <div className="app-shell">
        <main className="app-main">{renderRoute(route, navigate)}</main>
        {route.name !== 'onboarding' ? (
          <BottomNav active={activeTab} navigate={navigate} />
        ) : null}
      </div>
    </div>
  )
}

function renderRoute(route: Route, navigate: Navigate): ReactNode {
  switch (route.name) {
    case 'onboarding':
      return <OnboardingPage navigate={navigate} />
    case 'home':
      return <HomePage navigate={navigate} />
    case 'compare':
      return <ComparePage navigate={navigate} />
    case 'compare-filter':
      return <CompareFilterPage navigate={navigate} />
    case 'mission':
      return <MissionPage />
    case 'records':
      return <RecordsPage />
    case 'profile':
      return <ProfilePage navigate={navigate} />
    case 'privacy':
      return <PrivacyPage navigate={navigate} />
    case 'not-found':
      return <NotFoundPage navigate={navigate} />
  }
}

function getActiveTab(route: Route): TabKey {
  if (route.name === 'compare' || route.name === 'compare-filter') {
    return 'compare'
  }
  if (route.name === 'mission') {
    return 'mission'
  }
  if (route.name === 'records') {
    return 'records'
  }
  if (route.name === 'profile' || route.name === 'privacy') {
    return 'profile'
  }
  return 'home'
}

function HomePage({ navigate }: { navigate: Navigate }) {
  return (
    <Screen title="" right={<IconButton icon="bell" label="알림" />}>
      <section className="home-screen">
        <h1>{homeDemo.greeting}</h1>

        <button className="hero-mission-card" type="button" onClick={() => navigate('/missions')}>
          <div>
            <span>오늘의 미션</span>
            <strong>{homeDemo.missionTitle}</strong>
            <small>진행률 {homeDemo.missionProgress}%</small>
            <ProgressLine value={homeDemo.missionProgress} tone="purple" />
          </div>
          <Chevron />
        </button>

        <Card>
          <SectionHeader title="오늘의 예산" action="자세히 보기" />
          <div className="budget-row">
            <Metric label="하루 예산" value={homeDemo.budget.total} />
            <Metric label="사용 금액" value={homeDemo.budget.used} />
            <Metric label="남은 금액" value={homeDemo.budget.left} tone="green" />
          </div>
          <ProgressLine value={78} tone="green" />
        </Card>

        <Card>
          <SectionHeader title="오늘의 지출 요약" action="전체 보기" />
          <div className="spending-grid">
            {homeDemo.spending.map((item) => (
              <SpendingTile item={item} key={item.label} />
            ))}
          </div>
        </Card>

        <Card>
          <SectionHeader title="자산 현황" action="자세히 보기" />
          <div className="asset-card-body">
            <div>
              <span>총 자산</span>
              <strong>{homeDemo.assets.total}</strong>
              <small>
                이번 달 {homeDemo.assets.delta} <b>{homeDemo.assets.growth}</b>
              </small>
            </div>
            <MiniLineChart />
          </div>
        </Card>

        <Card>
          <SectionHeader title="팔로잉 금융 근황" action="전체 보기" />
          <p className="card-subtitle">친구 50명의 금융 활동 요약</p>
          <div className="signal-grid">
            {homeDemo.followers.map((item) => (
              <SignalTile signal={item} key={item.label} />
            ))}
          </div>
        </Card>
      </section>
    </Screen>
  )
}

function ComparePage({ navigate }: { navigate: Navigate }) {
  return (
    <Screen
      title="그룹 비교"
      left={<IconButton icon="back" label="뒤로" onClick={() => navigate('/home')} />}
      right={<IconButton icon="help" label="도움말" />}
    >
      <section className="compare-screen">
        <div className="lead-copy">
          <h1>비슷한 사람들과 비교해보세요</h1>
          <p>나와 비슷한 금융 생활을 가진 사람들의 평균과 비교할 수 있어요.</p>
        </div>

        <div className="score-grid">
          <ScoreCard title="나의 금융 점수" score={compareDemo.mineScore} rank={compareDemo.mineRank} />
          <ScoreCard
            title="비교 그룹 평균"
            score={compareDemo.groupScore}
            rank={compareDemo.groupRank}
            tone="mint"
          />
        </div>

        <Card className="compare-list-card">
          <SectionHeader title="항목별 비교" legend="나 · 그룹 평균" />
          <div className="compare-list">
            {compareDemo.metrics.map((metric) => (
              <CompareMetricRow metric={metric} key={metric.label} />
            ))}
          </div>
        </Card>

        <ActionPanel>
          <button className="primary-button" type="button" onClick={() => navigate('/compare/filter')}>
            비교 그룹 변경하기
          </button>
        </ActionPanel>
      </section>
    </Screen>
  )
}

function CompareFilterPage({ navigate }: { navigate: Navigate }) {
  return (
    <Screen
      title="비교 그룹 선택"
      left={<IconButton icon="back" label="뒤로" onClick={() => navigate('/compare')} />}
      right={<button className="text-button" type="button">초기화</button>}
    >
      <section className="filter-screen">
        <div className="section-mini-heading">
          <span>필터 선택</span>
        </div>
        <div className="filter-list">
          {filterDemo.filters.slice(0, 5).map(([label, value]) => (
            <FilterRow label={label} value={value} key={label} />
          ))}
        </div>

        <h2>추가 필터</h2>
        <div className="filter-list">
          {filterDemo.filters.slice(5).map(([label, value]) => (
            <FilterRow label={label} value={value} key={label} />
          ))}
        </div>

        <p className="result-count">이 조건에 맞는 사용자 1,246명</p>
        <div className="profile-rail">
          {filterDemo.profiles.map((profile) => (
            <FilterProfileCard profile={profile} key={profile.name} />
          ))}
        </div>

        <ActionPanel>
          <button className="primary-button" type="button" onClick={() => navigate('/compare')}>
            이 그룹으로 비교하기
          </button>
        </ActionPanel>
      </section>
    </Screen>
  )
}

function MissionPage() {
  return (
    <Screen title="미션" right={<IconButton icon="gift" label="리워드" />}>
      <section className="mission-screen">
        <div className="section-mini-heading spread">
          <span>오늘의 미션</span>
          <small>매일 00:00에 갱신돼요</small>
        </div>

        <Card className="mission-hero">
          <div>
            <strong>{missionDemo.heroTitle}</strong>
            <span>식비 미션</span>
            <b>{missionDemo.dailyBudget}</b>
            <small>하루 식비 예산</small>
          </div>
          <RingProgress value={missionDemo.progress} label="진행 중" />
          <ProgressLine value={missionDemo.progress} tone="purple" />
          <p>남은 예산 {missionDemo.left}</p>
        </Card>

        <SectionHeader title="진행 중인 미션" action="전체 보기" />
        <Card className="mission-list">
          {missionDemo.active.map((mission) => (
            <MissionRow mission={mission} key={mission.title} />
          ))}
        </Card>

        <SectionHeader title="완료한 미션" action="전체 보기" />
        <Card className="completed-mission">
          <IconBadge icon="check" tone="green" />
          <div>
            <strong>{missionDemo.completed.title}</strong>
            <span>{missionDemo.completed.description}</span>
          </div>
          <div>
            <b>{missionDemo.completed.points}</b>
            <small>{missionDemo.completed.date}</small>
          </div>
        </Card>

        <div className="points-strip">
          <span>나의 포인트</span>
          <strong>{missionDemo.points.total}</strong>
          <small>이번 주 획득 포인트 {missionDemo.points.weekly}</small>
        </div>
      </section>
    </Screen>
  )
}

function RecordsPage() {
  const weekdays = ['일', '월', '화', '수', '목', '금', '토']

  return (
    <Screen title="캘린더" right={<IconButton icon="chart" label="통계" />}>
      <section className="records-screen">
        <div className="month-heading">
          <Chevron direction="left" />
          <h1>{recordDemo.month}</h1>
          <Chevron />
        </div>

        <div className="calendar-grid weekdays">
          {weekdays.map((day) => (
            <span key={day}>{day}</span>
          ))}
        </div>
        <div className="calendar-grid">
          {recordDemo.days.map((day) => (
            <CalendarCell day={day} key={day.day} />
          ))}
        </div>

        <div className="legend-row">
          <LegendDot tone="success" label="미션 성공" />
          <LegendDot tone="over" label="예산 초과" />
          <LegendDot tone="none" label="기록 없음" />
        </div>

        <Card>
          <div className="record-budget">
            <div>
              <span>오늘의 예산</span>
              <small>식비 미션 진행 중</small>
            </div>
            <div className="budget-row compact">
              <Metric label="하루 예산" value="₩10,000" />
              <Metric label="사용 금액" value="₩7,800" />
              <Metric label="사용률" value="78%" />
            </div>
            <ProgressLine value={78} tone="green" />
            <p>남은 예산 <b>₩2,200</b></p>
          </div>
        </Card>

        <Card>
          <SectionHeader title="오늘의 미션 기록" legend="+120P" />
          <div className="record-item">
            <IconBadge icon="food" tone="mint" />
            <div>
              <strong>식비 10,000원 이하 사용하기</strong>
              <span>성공! 사용 금액 7,800원</span>
            </div>
          </div>
        </Card>
      </section>
    </Screen>
  )
}

function ProfilePage({ navigate }: { navigate: Navigate }) {
  return (
    <Screen
      title="내 팔로워 인사이트"
      left={<IconButton icon="back" label="뒤로" onClick={() => navigate('/home')} />}
    >
      <section className="profile-screen">
        <button className="follower-card" type="button" onClick={() => navigate('/settings/privacy')}>
          <IconBadge icon="users" tone="purple" />
          <div>
            <strong>내 팔로워 {profileDemo.followers}</strong>
            <span>함께 성장하고 있는 금융 생활을 확인해보세요!</span>
          </div>
          <Chevron />
        </button>

        <SectionHeader title="팔로워 금융 생활 요약" />
        <div className="profile-summary-grid">
          {profileDemo.summary.map((signal) => (
            <SummaryCard signal={signal} key={signal.label} />
          ))}
        </div>

        <Card>
          <SectionHeader title="금융 생활 분포" />
          <div className="distribution-list">
            {profileDemo.distribution.map((signal) => (
              <DistributionRow signal={signal} key={signal.label} />
            ))}
          </div>
        </Card>

        <Card>
          <SectionHeader title="팔로잉 TOP 5 금융 활동" />
          <ol className="activity-list">
            {['이지연', '김민수', '박상우', '최유진', '정하나'].map((name, index) => (
              <li key={name}>
                <span>{index + 1}</span>
                <b>{name}</b>
                <small>{index % 2 === 0 ? '적금' : '주식'}</small>
                <em>+{[240000, 520000, 180000, 120000, 300000][index].toLocaleString('ko-KR')}원</em>
              </li>
            ))}
          </ol>
        </Card>
      </section>
    </Screen>
  )
}

function PrivacyPage({ navigate }: { navigate: Navigate }) {
  const [withdrawn, setWithdrawn] = useState(false)

  return (
    <Screen
      title="공개 설정"
      left={<IconButton icon="back" label="뒤로" onClick={() => navigate('/profile')} />}
    >
      <section className="privacy-screen">
        <Card className="privacy-preview">
          <span>공개 미리보기</span>
          <h1>나의 공개 미리보기</h1>
          <p>내 익명 포트폴리오에는 필요한 요약 정보만 보여요.</p>
        </Card>

        <Card>
          <h2>보이는 정보</h2>
          <div className="chip-row">
            {privacyVisible.map((field) => (
              <span className="chip safe" key={field}>{field}</span>
            ))}
          </div>
        </Card>

        <Card>
          <h2>숨겨지는 정보</h2>
          <div className="chip-row">
            {privacyHidden.map((field) => (
              <span className="chip muted" key={field}>{field}</span>
            ))}
          </div>
        </Card>

        <Card className="danger-card">
          <h2>공개 철회</h2>
          <p>철회하면 내 익명 포트폴리오는 더 이상 또래 탐색 화면에 노출되지 않아요.</p>
          <button className="danger-button" type="button" onClick={() => setWithdrawn(true)}>
            익명 포트폴리오 공개 철회
          </button>
        </Card>

        {withdrawn ? (
          <p className="toast">공개 동의가 철회됐어요. 내 익명 포트폴리오는 더 이상 탐색되지 않습니다.</p>
        ) : null}
      </section>
    </Screen>
  )
}

function OnboardingPage({ navigate }: { navigate: Navigate }) {
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [steps, setSteps] = useState<OnboardingStep[]>([
    { label: '지금 상태 선택', status: 'pending', detail: '이번 P0는 학생/알바 기준으로 시연돼요' },
    { label: '금융 목표 선택', status: 'pending', detail: '비상금 만들기 루틴을 기준으로 비교합니다' },
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
      markStep(0, 'done', '학생/알바 기준으로 준비했어요')

      markStep(1, 'loading', '선택한 목표를 연결하고 있어요')
      const mockConsent = await api.createMockConsent(
        diagnosis.onboardingToken,
        diagnosis.diagnosisId,
      )
      saveSession({ mydataConnectionId: mockConsent.mydataConnectionId })
      markStep(1, 'done', '비상금 만들기 목표로 체험합니다')

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
    <Screen title="FinMate">
      <section className="onboarding-screen">
        <div className="lead-copy">
          <h1>나에게 맞는 금융 습관을 시작해볼까요?</h1>
          <p>합성 마이데이터로 비교, 미션, 기록, 프로필 화면을 안전하게 체험합니다.</p>
        </div>

        <Card className="demo-note">
          <span>이번 P0 데모</span>
          <strong>비상금 루틴 기준 고정 시연</strong>
          <p>다른 상태와 목표는 다음 버전에서 연결될 예정이에요.</p>
        </Card>

        <Card className="choice-card">
          <StepTitle index={1} step={steps[0]} />
          <ChoiceGrid items={onboardingStatuses} />
        </Card>

        <Card className="choice-card">
          <StepTitle index={2} step={steps[1]} />
          <ChoiceGrid items={onboardingGoals} />
        </Card>

        {steps.slice(2).map((step, index) => (
          <Card className="choice-card" key={step.label}>
            <StepTitle index={index + 3} step={step} />
          </Card>
        ))}

        {error ? <p className="inline-error">{error}</p> : null}

        <ActionPanel>
          <button className="primary-button" type="button" onClick={startOnboarding} disabled={running}>
            {running ? '준비 중이에요' : '동의하고 시작하기'}
          </button>
          <button type="button" onClick={() => navigate('/home')}>
            바로 보기
          </button>
        </ActionPanel>
      </section>
    </Screen>
  )
}

function NotFoundPage({ navigate }: { navigate: Navigate }) {
  return (
    <Screen title="안내">
      <Card className="empty-state">
        <h1>화면을 찾을 수 없어요</h1>
        <p>홈으로 이동해 5탭 데모를 다시 확인해주세요.</p>
        <button className="primary-button" type="button" onClick={() => navigate('/home')}>
          홈으로 이동
        </button>
      </Card>
    </Screen>
  )
}

function Screen({
  title,
  left,
  right,
  children,
}: {
  title: string
  left?: ReactNode
  right?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="screen">
      <header className="app-header">
        <div className="header-side">{left}</div>
        <strong>{title}</strong>
        <div className="header-side right">{right}</div>
      </header>
      {children}
    </div>
  )
}

function BottomNav({ active, navigate }: { active: TabKey; navigate: Navigate }) {
  const items: Array<{ key: TabKey; label: string; path: string; icon: string }> = [
    { key: 'home', label: '홈', path: '/home', icon: 'home' },
    { key: 'compare', label: '비교', path: '/compare', icon: 'search' },
    { key: 'mission', label: '미션', path: '/missions', icon: 'mission' },
    { key: 'records', label: '기록', path: '/records', icon: 'records' },
    { key: 'profile', label: '프로필', path: '/profile', icon: 'profile' },
  ]

  return (
    <nav className="bottom-nav" aria-label="하단 탭">
      {items.map((item) => (
        <button
          className={active === item.key ? 'active' : undefined}
          type="button"
          key={item.key}
          onClick={() => navigate(item.path)}
        >
          <AppIcon name={item.icon} />
          <span>{item.label}</span>
        </button>
      ))}
    </nav>
  )
}

function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <article className={`card ${className}`}>{children}</article>
}

function SectionHeader({
  title,
  action,
  legend,
}: {
  title: string
  action?: string
  legend?: string
}) {
  return (
    <div className="section-header">
      <h2>{title}</h2>
      {action ? <button className="text-button" type="button">{action}</button> : null}
      {legend ? <span>{legend}</span> : null}
    </div>
  )
}

function Metric({ label, value, tone }: { label: string; value: string; tone?: 'green' }) {
  return (
    <div className={`metric ${tone ?? ''}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function SpendingTile({ item }: { item: SpendingCategory }) {
  return (
    <div className="spending-tile">
      <IconBadge icon={item.icon} tone={item.icon === 'transport' ? 'blue' : 'warm'} />
      <strong>{item.label}</strong>
      <span>{item.amount}</span>
      <small>{item.ratio}</small>
    </div>
  )
}

function SignalTile({ signal }: { signal: FinanceSignal }) {
  return (
    <div className="signal-tile">
      <IconBadge icon={signal.icon} tone="mint" />
      <span>{signal.label}</span>
      <strong>{signal.count}</strong>
    </div>
  )
}

function SummaryCard({ signal }: { signal: FinanceSignal }) {
  return (
    <Card className="summary-card">
      <span>{signal.label}</span>
      <strong>{signal.count}</strong>
      <small>{signal.ratio}</small>
    </Card>
  )
}

function ScoreCard({
  title,
  score,
  rank,
  tone = 'purple',
}: {
  title: string
  score: string
  rank: string
  tone?: 'purple' | 'mint'
}) {
  return (
    <div className={`score-card ${tone}`}>
      <span>{title}</span>
      <strong>{score}</strong>
      <small>{rank}</small>
    </div>
  )
}

function CompareMetricRow({ metric }: { metric: CompareMetric }) {
  return (
    <div className="compare-metric-row">
      <IconBadge icon={metric.icon} tone="mint" />
      <div className="metric-copy">
        <strong>{metric.label}</strong>
        <span>{metric.caption}</span>
      </div>
      <div className="dual-bars">
        <ProgressLine value={metric.mineRatio} tone="purple" />
        <ProgressLine value={metric.groupRatio} tone="gray" />
      </div>
      <div className="metric-values">
        <strong>{metric.mine}</strong>
        <span>{metric.group}</span>
      </div>
    </div>
  )
}

function FilterRow({ label, value }: { label: string; value: string }) {
  return (
    <button className="filter-row" type="button">
      <span>{label}</span>
      <strong>{value}</strong>
      <Chevron />
    </button>
  )
}

function FilterProfileCard({ profile }: { profile: FilterProfile }) {
  return (
    <Card className="filter-profile-card">
      <div className="avatar">{profile.initials}</div>
      <strong>{profile.name}</strong>
      <span>{profile.age} · {profile.job}</span>
      <small>{profile.income}</small>
      <p>금융 점수 <b>{profile.score}</b></p>
      <button type="button">+ 팔로우</button>
    </Card>
  )
}

function MissionRow({ mission }: { mission: MissionItem }) {
  const progressValue = useMemo(() => {
    const [done, total] = mission.progress.split('/').map((part) => Number(part.trim()))
    return total ? Math.round((done / total) * 100) : 0
  }, [mission.progress])

  return (
    <div className="mission-row">
      <IconBadge icon={mission.icon} tone="warm" />
      <div>
        <strong>{mission.title}</strong>
        <span>{mission.description}</span>
        <ProgressLine value={progressValue} tone="purple" />
      </div>
      <div>
        <small>{mission.progress}</small>
        <b>{mission.points}</b>
      </div>
    </div>
  )
}

function CalendarCell({ day }: { day: CalendarDay }) {
  return (
    <button className={`calendar-cell ${day.state ?? ''}`} type="button">
      <strong>{day.day}</strong>
      {day.amount ? <span>{day.amount}</span> : null}
      {day.state && day.state !== 'selected' ? <i /> : null}
    </button>
  )
}

function DistributionRow({ signal }: { signal: FinanceSignal }) {
  const numeric = Number(signal.count.replace('%', ''))

  return (
    <div className="distribution-row">
      <IconBadge icon={signal.icon} tone="soft" />
      <span>{signal.label}</span>
      <ProgressLine value={numeric} tone="purple" />
      <small>{signal.count} ({signal.ratio})</small>
    </div>
  )
}

function LegendDot({ tone, label }: { tone: 'success' | 'over' | 'none'; label: string }) {
  return (
    <span className={`legend-dot ${tone}`}>
      <i />
      {label}
    </span>
  )
}

function ActionPanel({ children }: { children: ReactNode }) {
  return <div className="action-panel">{children}</div>
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

function ChoiceGrid({
  items,
}: {
  items: Array<{ label: string; detail: string; available: boolean }>
}) {
  return (
    <div className="choice-grid">
      {items.map((item) => (
        <button
          className={`choice-chip ${item.available ? 'selected' : 'unavailable'}`}
          type="button"
          key={item.label}
          disabled={!item.available}
        >
          <span>{item.label}</span>
          <small>{item.detail}</small>
        </button>
      ))}
    </div>
  )
}

function RingProgress({ value, label }: { value: number; label: string }) {
  return (
    <div className="ring-progress" style={{ '--value': `${value}%` } as React.CSSProperties}>
      <strong>{value}%</strong>
      <span>{label}</span>
    </div>
  )
}

function ProgressLine({ value, tone }: { value: number; tone: 'purple' | 'green' | 'gray' }) {
  return (
    <div className={`progress-line ${tone}`} aria-hidden="true">
      <span style={{ width: `${Math.min(100, Math.max(0, value))}%` }} />
    </div>
  )
}

function MiniLineChart() {
  return (
    <svg className="mini-line-chart" viewBox="0 0 148 68" aria-hidden="true">
      <path d="M4 56 C18 44 26 48 38 38 S62 30 74 36 94 12 108 24 130 36 144 16" />
      <circle cx="108" cy="24" r="4" />
    </svg>
  )
}

function IconBadge({
  icon,
  tone,
}: {
  icon: string
  tone: 'purple' | 'mint' | 'warm' | 'blue' | 'green' | 'soft'
}) {
  return (
    <span className={`icon-badge ${tone}`}>
      <AppIcon name={icon} />
    </span>
  )
}

function IconButton({
  icon,
  label,
  onClick,
}: {
  icon: string
  label: string
  onClick?: () => void
}) {
  return (
    <button className="icon-button" type="button" aria-label={label} onClick={onClick}>
      <AppIcon name={icon} />
    </button>
  )
}

function Chevron({ direction = 'right' }: { direction?: 'left' | 'right' }) {
  return (
    <svg className={`chevron ${direction}`} viewBox="0 0 24 24" aria-hidden="true">
      <path d="m9 5 7 7-7 7" />
    </svg>
  )
}

function AppIcon({ name }: { name: string }) {
  switch (name) {
    case 'home':
      return <svg viewBox="0 0 24 24"><path d="M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z" /></svg>
    case 'search':
      return <svg viewBox="0 0 24 24"><path d="M10.5 18a7.5 7.5 0 1 1 5.2-2.1l4.2 4.2-1.8 1.8-4.2-4.2a7.4 7.4 0 0 1-3.4.8Zm0-2.4a5.1 5.1 0 1 0 0-10.2 5.1 5.1 0 0 0 0 10.2Z" /></svg>
    case 'mission':
      return <svg viewBox="0 0 24 24"><path d="M7 4h10a2 2 0 0 1 2 2v14H5V6a2 2 0 0 1 2-2Zm2 4v2h6V8H9Zm0 4v2h6v-2H9Zm0 4v2h4v-2H9Z" /></svg>
    case 'records':
      return <svg viewBox="0 0 24 24"><path d="M7 3h2v2h6V3h2v2h2a2 2 0 0 1 2 2v13H3V7a2 2 0 0 1 2-2h2V3Zm12 8H5v7h14v-7Z" /></svg>
    case 'profile':
      return <svg viewBox="0 0 24 24"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-8 9c.7-4.2 3.4-6.4 8-6.4s7.3 2.2 8 6.4H4Z" /></svg>
    case 'bell':
      return <svg viewBox="0 0 24 24"><path d="M12 22a2.6 2.6 0 0 0 2.5-2h-5a2.6 2.6 0 0 0 2.5 2Zm7-5H5l1.8-2.5V10a5.2 5.2 0 0 1 10.4 0v4.5L19 17Z" /></svg>
    case 'back':
      return <svg viewBox="0 0 24 24"><path d="M15.5 4.5 8 12l7.5 7.5-1.8 1.8L4.4 12l9.3-9.3 1.8 1.8Z" /></svg>
    case 'help':
      return <svg viewBox="0 0 24 24"><path d="M11 17h2v2h-2v-2Zm1-14a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm0 2.3a6.7 6.7 0 1 1 0 13.4 6.7 6.7 0 0 1 0-13.4Zm0 2.2c-1.7 0-3 1-3.2 2.6h2c.1-.6.5-1 1.2-1 .8 0 1.3.5 1.3 1.2 0 .6-.3 1-1.1 1.5-1 .7-1.4 1.4-1.3 2.6h1.9c0-.7.3-1.1 1-1.6.9-.6 1.5-1.4 1.5-2.7 0-1.7-1.3-2.8-3.3-2.8Z" /></svg>
    case 'gift':
      return <svg viewBox="0 0 24 24"><path d="M20 7h-2.1A3.2 3.2 0 0 0 12 5.4 3.2 3.2 0 0 0 6.1 7H4v5h1v8h14v-8h1V7Zm-9 11H7v-6h4v6Zm0-8H6V9h5v1Zm-2.2-3A1.2 1.2 0 1 1 11 6.4V7H8.8Zm4.2-.6A1.2 1.2 0 1 1 15.2 7H13v-.6ZM17 18h-4v-6h4v6Zm1-8h-5V9h5v1Z" /></svg>
    case 'chart':
      return <svg viewBox="0 0 24 24"><path d="M5 20h14v2H3V4h2v16Zm2-2V9h3v9H7Zm5 0V4h3v14h-3Zm5 0v-6h3v6h-3Z" /></svg>
    case 'food':
      return <svg viewBox="0 0 24 24"><path d="M7 3h2v8a2 2 0 0 1-1 1.7V21H6v-8.3A2 2 0 0 1 5 11V3h2v6h1V3Zm8 0h2v18h-2v-7h-2V7a4 4 0 0 1 2-3.5V3Z" /></svg>
    case 'transport':
      return <svg viewBox="0 0 24 24"><path d="M6 4h12a3 3 0 0 1 3 3v9a2 2 0 0 1-2 2l1 2h-2.3l-1-2H7.3l-1 2H4l1-2a2 2 0 0 1-2-2V7a3 3 0 0 1 3-3Zm0 3v4h12V7H6Zm1 8a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Zm10 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z" /></svg>
    case 'cafe':
      return <svg viewBox="0 0 24 24"><path d="M4 6h12v7a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V6Zm12 2h2a3 3 0 0 1 0 6h-2v-2h2a1 1 0 0 0 0-2h-2V8ZM5 20h13v2H5v-2Z" /></svg>
    case 'more':
      return <svg viewBox="0 0 24 24"><path d="M6 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm6 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm6 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" /></svg>
    case 'stocks':
      return <svg viewBox="0 0 24 24"><path d="m4 16 5-5 3 3 6-8 2 1.5-7.7 10.2-3.1-3.1L5.5 18 4 16Zm1 4h15v2H5v-2Z" /></svg>
    case 'saving':
      return <svg viewBox="0 0 24 24"><path d="M12 3a7 7 0 0 1 7 7v1h2v4h-2.5A7 7 0 0 1 5 12V9a6 6 0 0 1 6-6h1Zm-1 5h4V6h-4v2Zm-3 4a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z" /></svg>
    case 'fund':
      return <svg viewBox="0 0 24 24"><path d="M12 3 4 7l8 4 8-4-8-4Zm-6 8 6 3 6-3v6l-6 4-6-4v-6Z" /></svg>
    case 'pension':
      return <svg viewBox="0 0 24 24"><path d="M5 8h14v12H5V8Zm2-5h10v3H7V3Zm2 9h6v2H9v-2Zm0 4h4v2H9v-2Z" /></svg>
    case 'study':
      return <svg viewBox="0 0 24 24"><path d="M4 5h7a3 3 0 0 1 3 3v11a3 3 0 0 0-3-2H4V5Zm16 0v12h-7a3 3 0 0 0-3 2V8a3 3 0 0 1 3-3h7Z" /></svg>
    case 'spend':
      return <svg viewBox="0 0 24 24"><path d="M4 6h16v12H4V6Zm2 3v6h12V9H6Zm2 2h5v2H8v-2Z" /></svg>
    case 'debt':
      return <svg viewBox="0 0 24 24"><path d="M5 10V7a7 7 0 0 1 14 0v3h1v11H4V10h1Zm3 0h8V7a4 4 0 0 0-8 0v3Zm4 3a2 2 0 0 0-1 3.7V18h2v-1.3a2 2 0 0 0-1-3.7Z" /></svg>
    case 'cart':
      return <svg viewBox="0 0 24 24"><path d="M7 18a2 2 0 1 0 0 4 2 2 0 0 0 0-4Zm10 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM3 4h2l2.2 10.5H17l3-7.5H7.1L6.7 5H3V4Z" /></svg>
    case 'piggy':
      return <svg viewBox="0 0 24 24"><path d="M6 10a6 6 0 0 1 6-5h3a5 5 0 0 1 4.7 3.2H22v5h-2.2a6 6 0 0 1-2.8 3.3V20h-3v-2h-4v2H7v-3.2A6 6 0 0 1 6 10Zm9-2h-5v2h5V8Z" /></svg>
    case 'check':
      return <svg viewBox="0 0 24 24"><path d="m9.4 16.6-4-4L4 14l5.4 5.4L21 7.8 19.6 6.4 9.4 16.6Z" /></svg>
    case 'users':
      return <svg viewBox="0 0 24 24"><path d="M8 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8-1a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM2 21c.5-4.5 2.8-6.8 6-6.8s5.5 2.3 6 6.8H2Zm12.2 0a9 9 0 0 0-2-4.7 5 5 0 0 1 3.8-1.5c2.9 0 5 2.1 5.5 6.2h-7.3Z" /></svg>
    default:
      return null
  }
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

export default App
