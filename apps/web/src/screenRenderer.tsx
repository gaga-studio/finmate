import { useState, type CSSProperties, type ReactNode } from 'react'
import { api } from './api'
import { clearSession } from './session'
import type { AppAction, AppCompareSearchRequest, AppItem, AppMetric, AppScreenResponse, AppSection } from './types'
import type { Navigate } from './navigation'
import {
  Chevron,
  IconBadge,
  IconButton,
  Legend,
  MiniLineChart,
  ProgressLine,
  RingProgress,
  StatusBar,
  type IconName,
} from './uiPrimitives'
import { arrayFromData, numberFromData } from './screenData'

type SectionProps = {
  section: AppSection
  navigate: Navigate
}

export function ScreenRenderer({ screen, navigate }: { screen: AppScreenResponse; navigate: Navigate }) {
  const canGoBack = !['home', 'compare', 'missions', 'records:2026-06', 'profile'].includes(screen.screenId)

  if (screen.screenId === 'home') {
    return <HomeScreen screen={screen} navigate={navigate} />
  }

  return (
    <div className={`screen screen-${screen.tab} ${screenClass(screen.screenId)}`}>
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

function HomeScreen({ screen, navigate }: { screen: AppScreenResponse; navigate: Navigate }) {
  const greeting = screen.sections.find((section) => section.kind === 'greeting')
  const content = screen.sections.filter((section) => section.kind !== 'greeting')

  return (
    <div className={`screen screen-home screen-home-reference ${screenClass(screen.screenId)}`}>
      <StatusBar time={screen.statusBarTime} />
      <header className="home-app-header">
        <div>
          <h1>{greeting?.title ?? '좋은 아침이에요'}</h1>
          {greeting?.subtitle ? <p>{greeting.subtitle}</p> : null}
        </div>
        <IconButton icon="bell" label="알림" onClick={() => navigate('/birthdays')} />
      </header>
      <section className="home-stack">
        {content.map((section) => (
          <HomeSection section={section} navigate={navigate} key={section.id} />
        ))}
      </section>
    </div>
  )
}

function HomeSection({ section, navigate }: SectionProps) {
  if (section.kind === 'missionHero') {
    return <HomeMissionCard section={section} navigate={navigate} />
  }
  if (section.kind === 'budget') {
    return <HomeBudgetCard section={section} navigate={navigate} />
  }
  if (section.kind === 'spendingGrid') {
    return <HomeSpendingCard section={section} navigate={navigate} />
  }
  if (section.kind === 'asset') {
    return <HomeAssetCard section={section} navigate={navigate} />
  }
  if (section.kind === 'signalGrid') {
    return <HomeFollowingCard section={section} navigate={navigate} />
  }
  if (section.kind === 'actionCard' && section.id === 'birthday-alert') {
    return <HomeBirthdayCard section={section} navigate={navigate} />
  }
  if (section.kind === 'actionCard') {
    return <HomeEmptyCard section={section} navigate={navigate} />
  }
  return <SectionRenderer section={section} navigate={navigate} />
}

function HomeMissionCard({ section, navigate }: SectionProps) {
  const metric = section.metrics?.[0]
  return (
    <button className="home-card home-mission-card" type="button" onClick={() => goDetail(section, navigate)}>
      <div className="home-card-copy">
        <div className="home-card-head">
          <span>오늘의 미션</span>
          <Chevron />
        </div>
        <strong>{section.title}</strong>
        <small>{metric?.label ?? '진행률'} {metric?.value ?? '0%'}</small>
        <ProgressLine value={metric?.progress ?? 0} tone="purple" />
      </div>
      <img className="home-character" src={section.heroAsset ?? '/assets/characters/finmate-main.png'} alt="" />
    </button>
  )
}

function HomeBudgetCard({ section, navigate }: SectionProps) {
  const progress = numberFromData(section.data, 'progress') ?? section.metrics?.[1]?.progress ?? 0
  return (
    <article className="home-card home-budget-card">
      <HomeCardHeader section={section} navigate={navigate} />
      <div className="home-budget-grid">
        {section.metrics?.map((metric) => <MetricView metric={metric} key={metric.label} />)}
      </div>
      <ProgressLine value={progress} tone="green" />
    </article>
  )
}

function HomeSpendingCard({ section, navigate }: SectionProps) {
  return (
    <article className="home-card home-spending-card">
      <HomeCardHeader section={section} navigate={navigate} />
      <div className="home-spending-grid">
        {section.items?.slice(0, 4).map((item) => (
          <button type="button" onClick={() => item.detailPath && navigate(item.detailPath)} key={item.id}>
            <IconBadge icon={(item.icon ?? 'more') as IconName} tone={item.tone ?? 'warning'} />
            <strong>{item.title}</strong>
            {item.value ? <b>{item.value}</b> : null}
            {item.caption ? <small>{item.caption}</small> : null}
          </button>
        ))}
      </div>
    </article>
  )
}

function HomeAssetCard({ section, navigate }: SectionProps) {
  const sparkline = arrayFromData(section.data, 'sparkline')
  return (
    <article className="home-card home-asset-card">
      <HomeCardHeader section={section} navigate={navigate} />
      <div className="home-asset-layout">
        <div>
          {section.metrics?.map((metric) => <MetricView metric={metric} key={metric.label} />)}
          {section.subtitle ? <p>{section.subtitle}</p> : null}
        </div>
        {sparkline.length >= 2 ? <MiniLineChart values={sparkline} /> : <ChartEmptyLabel />}
      </div>
    </article>
  )
}

function HomeFollowingCard({ section, navigate }: SectionProps) {
  return (
    <article className="home-card home-following-card">
      <HomeCardHeader section={section} navigate={navigate} />
      {section.subtitle ? <p>{section.subtitle}</p> : null}
      <div className="home-following-grid">
        {section.metrics?.map((metric) => (
          <div className="home-following-stat" key={metric.label}>
            <IconBadge icon={metric.label.includes('주식') ? 'stocks' : metric.label.includes('적금') ? 'saving' : metric.label.includes('펀드') ? 'chart' : 'wallet'} tone={metric.tone ?? 'purple'} />
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
          </div>
        ))}
      </div>
    </article>
  )
}

function HomeBirthdayCard({ section, navigate }: SectionProps) {
  return (
    <article className="home-card home-birthday-card">
      <div className="home-birthday-layout">
        <div>
          <HomeCardHeader section={section} navigate={navigate} />
          {section.subtitle ? <p>{section.subtitle}</p> : null}
          <div className="home-budget-grid compact">
            {section.metrics?.map((metric) => <MetricView metric={metric} key={metric.label} />)}
          </div>
        </div>
        {section.heroAsset ? <img className="home-character" src={section.heroAsset} alt="" /> : null}
      </div>
      {section.metrics?.[0]?.progress ? <ProgressLine value={section.metrics[0].progress ?? 0} tone="green" /> : null}
      <ActionButtons actions={section.actions} navigate={navigate} />
    </article>
  )
}

function HomeEmptyCard({ section, navigate }: SectionProps) {
  return (
    <article className="home-card home-empty-card">
      <HomeCardHeader section={section} navigate={navigate} />
      {section.subtitle ? <p>{section.subtitle}</p> : null}
      {section.metrics?.[0]?.caption ? <small>{section.metrics[0].caption}</small> : null}
      <ActionButtons actions={section.actions} navigate={navigate} />
    </article>
  )
}

function HomeCardHeader({ section, navigate }: { section: AppSection; navigate: Navigate }) {
  return (
    <div className="home-section-header">
      <h2>{section.title}</h2>
      {section.detailPath ? (
        <button type="button" onClick={() => navigate(section.detailPath ?? '/home')}>
          자세히 보기 <Chevron />
        </button>
      ) : null}
    </div>
  )
}

export function LoadingScreen() {
  return (
    <div className="screen center-screen">
      <StatusBar time="9:41" />
      <div className="loader" />
      <p>화면을 불러오고 있어요</p>
    </div>
  )
}

export function ErrorScreen({ message, navigate }: { message: string; navigate: Navigate }) {
  return (
    <div className="screen center-screen">
      <StatusBar time="9:41" />
      <IconBadge icon="help" tone="danger" />
      <h1>화면을 불러오지 못했어요</h1>
      <p>{message}</p>
      <button className="app-button primary" type="button" onClick={() => navigate('/home')}>홈으로</button>
    </div>
  )
}

export function NotFoundPage({ navigate }: { navigate: Navigate }) {
  return (
    <div className="screen center-screen">
      <StatusBar time="9:41" />
      <IconBadge icon="search" tone="purple" />
      <h1>없는 화면이에요</h1>
      <p>다시 홈에서 시작해볼게요.</p>
      <button className="app-button primary" type="button" onClick={() => navigate('/home')}>홈으로</button>
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
  if (section.kind === 'comparePrompt') {
    return <ComparePromptSection section={section} navigate={navigate} />
  }
  if (section.kind === 'compareGroupRail') {
    return <CompareGroupRailSection section={section} navigate={navigate} />
  }
  if (section.kind === 'savedCompareGroups') {
    return <SavedCompareGroupsSection section={section} navigate={navigate} />
  }
  if (section.kind === 'compareProfileList') {
    return <CompareProfileListSection section={section} navigate={navigate} />
  }
  if (section.kind === 'compareGroupMembers') {
    return <CompareGroupMembersSection section={section} navigate={navigate} />
  }
  if (section.kind === 'profileSegmented') {
    return <ProfileSegmentedSection section={section} navigate={navigate} />
  }
  if (section.kind === 'profileFollowingHero') {
    return <ProfileFollowingHeroSection section={section} navigate={navigate} />
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
      <span className="surface-label">{section.kind === 'greeting' ? '오늘의 금융 루틴' : '확인할 인사이트'}</span>
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
    <Card section={section} navigate={navigate} className="budget-card">
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
  const hasMetricTiles = !isScore && section.metrics && section.metrics.length > 0
  return (
    <Card section={section} navigate={navigate} className={isScore ? 'score-card' : undefined}>
      {isScore ? (
        <div className="score-grid">
          {section.metrics?.map((metric) => <ScoreTile metric={metric} key={metric.label} />)}
        </div>
      ) : hasMetricTiles ? (
        <div className="tile-grid metric-tile-grid">
          {section.metrics?.map((metric) => <MetricTile metric={metric} key={metric.label} />)}
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
  const sparkline = arrayFromData(section.data, 'sparkline')
  return (
    <Card section={section} navigate={navigate} className="asset-card">
      <div className="asset-layout">
        <div>
          {section.metrics?.map((metric) => (
            <MetricView metric={metric} key={metric.label} />
          ))}
        </div>
        {sparkline.length >= 2 ? <MiniLineChart values={sparkline} /> : <ChartEmptyLabel />}
      </div>
    </Card>
  )
}

function ChartEmptyLabel() {
  return <span className="chart-empty-label">추세 데이터 부족</span>
}

function CompareBarsSection({ section, navigate }: SectionProps) {
  return (
    <Card section={section} navigate={navigate} className="compare-card">
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
    <Card section={section} navigate={navigate} className={`illustrated-card ${section.kind} section-${section.id}`}>
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
    <Card section={section} navigate={navigate} className={section.kind === 'points' ? 'points-card' : 'profile-summary-card'}>
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

function ComparePromptSection({ section, navigate }: SectionProps) {
  return (
    <button className="compare-prompt-card" type="button" onClick={() => goDetail(section, navigate)}>
      <span>{section.title}</span>
      <Chevron />
    </button>
  )
}

function CompareGroupRailSection({ section, navigate }: SectionProps) {
  const [pendingId, setPendingId] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const handleRecommendedGroupClick = async (item: AppItem) => {
    const filters = compareFiltersFromItem(item)
    if (!filters) {
      if (item.detailPath) {
        navigate(item.detailPath)
      }
      return
    }

    setPendingId(item.id)
    setNotice(null)
    try {
      const result = await api.createAppCompareGroup(filters)
      if (result.nextPath) {
        navigate(result.nextPath)
        return
      }
      setNotice(result.message)
    } catch {
      setNotice('추천 그룹을 불러오지 못했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setPendingId(null)
    }
  }

  return (
    <article className="card compare-group-section">
      <SectionHeader section={section} navigate={navigate} />
      {section.subtitle ? <p className="card-subtitle">{section.subtitle}</p> : null}
      <div className="compare-group-rail">
        {section.items?.map((item) => (
          <button
            className="compare-group-card"
            type="button"
            disabled={pendingId !== null}
            aria-busy={pendingId === item.id}
            onClick={() => { void handleRecommendedGroupClick(item) }}
            key={item.id}
          >
            <IconBadge icon={item.icon ?? 'profile'} tone={item.tone ?? 'purple'} />
            <strong>{item.title}</strong>
            {item.subtitle ? <span>{item.subtitle}</span> : null}
            {pendingId === item.id ? <small>비교 준비 중</small> : item.caption ? <small>{item.caption}</small> : null}
          </button>
        ))}
      </div>
      {notice ? <p className="inline-notice compare-group-notice">{notice}</p> : null}
    </article>
  )
}

function SavedCompareGroupsSection({ section, navigate }: SectionProps) {
  return (
    <article className="card saved-compare-section">
      <SectionHeader section={section} navigate={navigate} />
      {section.subtitle ? <p className="card-subtitle">{section.subtitle}</p> : null}
      <div className="saved-compare-list">
        {section.items?.map((item) => (
          <button className="saved-compare-card" type="button" onClick={() => item.detailPath && navigate(item.detailPath)} key={item.id}>
            <IconBadge icon={item.icon ?? 'profile'} tone={item.tone ?? 'purple'} />
            <div>
              <strong>{item.title}</strong>
              {item.subtitle ? <small>{item.subtitle}</small> : null}
            </div>
            {item.caption ? <span>{item.caption}</span> : null}
            <Chevron />
          </button>
        ))}
      </div>
      <ActionButtons actions={section.actions} navigate={navigate} />
    </article>
  )
}

function CompareProfileListSection({ section, navigate }: SectionProps) {
  return (
    <article className="card compare-profile-list-section">
      <SectionHeader section={section} navigate={navigate} />
      {section.subtitle ? <p className="card-subtitle">{section.subtitle}</p> : null}
      <div className="compare-profile-list">
        {section.items?.map((item) => (
          <button className="compare-profile-card" type="button" onClick={() => item.detailPath && navigate(item.detailPath)} key={item.id}>
            <IconBadge icon={item.icon ?? 'profile'} tone={item.tone ?? 'purple'} />
            <div className="compare-profile-copy">
              <strong>{item.title}</strong>
              {item.subtitle ? <span>{item.subtitle}</span> : null}
              {item.caption ? <small>{item.caption}</small> : null}
            </div>
            {item.value ? <b>{item.value}</b> : null}
          </button>
        ))}
      </div>
    </article>
  )
}

function CompareGroupMembersSection({ section, navigate }: SectionProps) {
  const items = section.items ?? []
  const pageSize = numberFromData(section.data, 'pageSize') ?? 5
  const initialVisible = numberFromData(section.data, 'initialVisible') ?? pageSize
  const [visibleCount, setVisibleCount] = useState(Math.min(items.length, initialVisible))
  const visibleItems = items.slice(0, visibleCount)
  const hasMore = visibleCount < items.length

  return (
    <article className="card compare-profile-list-section compare-group-members-section">
      <SectionHeader section={section} navigate={navigate} />
      {section.subtitle ? <p className="card-subtitle">{section.subtitle}</p> : null}
      <div className="compare-profile-list">
        {visibleItems.map((item) => (
          <CompareMemberCard item={item} key={item.id} />
        ))}
      </div>
      {hasMore ? (
        <button
          className="app-button secondary compare-members-more"
          type="button"
          onClick={() => setVisibleCount((count) => Math.min(items.length, count + pageSize))}
        >
          더보기 ({visibleCount}/{items.length})
        </button>
      ) : null}
    </article>
  )
}

function CompareMemberCard({ item }: { item: AppItem }) {
  const stock = item.data?.stockSignal === true
  const saving = item.data?.savingSignal === true
  const pension = item.data?.pensionSignal === true
  const ageBand = stringFromData(item.data, 'ageBand') ?? '나이 미공개'
  const jobCategory = stringFromData(item.data, 'jobCategory') ?? '직업 미공개'
  const incomeBand = stringFromData(item.data, 'incomeBand') ?? '미공개'
  const area = stringFromData(item.data, 'area') ?? '지역 미공개'
  const moneyStyle = stringFromData(item.data, 'moneyStyle') ?? '성향 미공개'
  const tags = [
    moneyStyle !== '성향 미공개' ? moneyStyle : '',
    stock ? '투자중' : '',
    saving ? '저축중' : '',
    pension ? '연금준비' : '',
  ].filter(Boolean).slice(0, 2)

  return (
    <article className="compare-profile-card compare-filter-profile-card compare-member-card">
      <div className="compare-profile-avatar" aria-hidden="true">
        <IconBadge icon="profile" tone="purple" />
      </div>
      <div className="compare-profile-main">
        <div className="compare-profile-name">
          <strong>{item.title}</strong>
          <span>{ageBand}</span>
        </div>
        <p>{jobCategory} · 연소득 {incomeBand}</p>
        <p>{area} · {moneyStyle}</p>
        {tags.length > 0 ? (
          <div className="compare-profile-tags" aria-label="프로필 태그">
            {tags.map((tag) => <span key={tag}>#{tag}</span>)}
          </div>
        ) : null}
      </div>
      <div className="compare-profile-signals" aria-label="금융 신호">
        <MemberSignal active={stock} label="주식" icon="stocks" />
        <MemberSignal active={saving} label="적금" icon="saving" />
        <MemberSignal active={pension} label="연금" icon="pension" />
      </div>
    </article>
  )
}

function MemberSignal({ active, label, icon }: { active: boolean; label: string; icon: string }) {
  return (
    <span className={active ? 'active' : ''}>
      <IconBadge icon={icon} tone={active ? 'purple' : 'muted'} />
      <b>{label}</b>
      <i aria-hidden="true" />
    </span>
  )
}

function ProfileSegmentedSection({ section, navigate }: SectionProps) {
  return (
    <div className="profile-segmented">
      {section.items?.map((item) => (
        <button className={item.caption ? 'active' : ''} type="button" onClick={() => item.detailPath && navigate(item.detailPath)} key={item.id}>
          <span>{item.title}</span>
          {item.subtitle ? <strong>{item.subtitle}</strong> : null}
        </button>
      ))}
    </div>
  )
}

function ProfileFollowingHeroSection({ section, navigate }: SectionProps) {
  return (
    <button className="profile-following-hero" type="button" onClick={() => goDetail(section, navigate)}>
      <IconBadge icon="profile" tone="purple" />
      <div>
        <strong>{section.title}</strong>
        {section.subtitle ? <span>{section.subtitle}</span> : null}
      </div>
      <Chevron />
    </button>
  )
}

function ListSection({ section, navigate }: SectionProps) {
  return (
    <Card
      section={section}
      navigate={navigate}
      className={`${section.id === 'feed' ? 'feed-card' : ''} section-${section.id}`}
    >
      <div className={section.kind === 'profileRail' ? 'profile-rail' : 'list-stack'}>
        {section.items?.map((item, index) => (
          <ListItem
            item={item}
            index={index}
            navigate={navigate}
            rank={section.kind === 'rankList'}
            variant={section.id}
            key={item.id}
          />
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
    <article className={`card ${section.kind}-section ${className ?? ''}`}>
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

function MetricTile({ metric }: { metric: AppMetric }) {
  return (
    <div className="mini-tile metric-mini-tile">
      <IconBadge icon={metric.label.includes('주식') ? 'stocks' : metric.label.includes('적금') ? 'saving' : metric.label.includes('펀드') ? 'fund' : 'pension'} tone={metric.tone ?? 'purple'} />
      <strong>{metric.label}</strong>
      <b>{metric.value}</b>
      {metric.caption ? <small>{metric.caption}</small> : null}
      {typeof metric.progress === 'number' ? <ProgressLine value={metric.progress} tone={metric.tone === 'green' ? 'green' : 'purple'} /> : null}
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
  const minePosition = clampPercent(numberFromData(item.data, 'minePosition') ?? mine)
  const rawGroupPosition = numberFromData(item.data, 'groupPosition') ?? group
  const groupPosition = clampPercent(rawGroupPosition === 0 ? 50 : rawGroupPosition)
  const deltaLabel = stringFromData(item.data, 'deltaLabel') ?? '그룹과 비교 중이에요'
  const deltaDirection = stringFromData(item.data, 'deltaDirection') ?? 'same'

  return (
    <button className="bar-row compare-diff-row" type="button" onClick={() => item.detailPath && navigate(item.detailPath)}>
      <IconBadge icon={item.icon ?? 'saving'} tone={item.tone ?? 'purple'} />
      <div className="bar-copy compare-diff-copy">
        <div className="compare-diff-heading">
          <div>
            <strong>{item.title}</strong>
            {item.subtitle ? <span>{cleanCaption(item.subtitle)}</span> : null}
          </div>
          <div className="bar-value">
            {item.value ? <b>{cleanCaption(item.value)}</b> : null}
            {item.caption ? <small>{cleanCaption(item.caption)}</small> : null}
          </div>
        </div>
        <CompareDiffGauge minePosition={minePosition} groupPosition={groupPosition} direction={deltaDirection} />
        <span className={`compare-diff-pill ${deltaDirection}`}>{deltaLabel}</span>
      </div>
    </button>
  )
}

function CompareDiffGauge({
  minePosition,
  groupPosition,
  direction,
}: {
  minePosition: number
  groupPosition: number
  direction: string
}) {
  const diffStart = Math.min(minePosition, groupPosition)
  const diffWidth = Math.abs(minePosition - groupPosition)
  const style = {
    '--mine-position': `${minePosition}%`,
    '--group-position': `${groupPosition}%`,
    '--diff-start': `${diffStart}%`,
    '--diff-width': `${diffWidth}%`,
  } as CSSProperties

  return (
    <div className={`compare-diff-gauge ${direction}`} style={style}>
      <div className="compare-diff-track" aria-label="그룹 평균 대비 내 위치">
        <span className="compare-diff-fill" />
        <span className="compare-diff-baseline"><b>그룹 평균</b></span>
        <span className="compare-diff-marker"><b>나</b></span>
      </div>
      <div className="compare-diff-scale" aria-hidden="true">
        <span>낮음</span>
        <span>높음</span>
      </div>
    </div>
  )
}

function ListItem({
  item,
  index,
  rank,
  variant,
  navigate,
}: {
  item: AppItem
  index: number
  rank: boolean
  variant?: string
  navigate: Navigate
}) {
  const inferred = inferItemPresentation(item, variant)
  const templateId = templateIdFromItem(item)

  return (
    <button
      className={`list-item ${variant === 'feed' ? 'feed-item' : ''}`}
      type="button"
      onClick={() => { void handleListItemClick(item, navigate, templateId) }}
    >
      {rank ? <span className="rank-dot">{index + 1}</span> : <IconBadge icon={inferred.icon} tone={inferred.tone} />}
      <div className="list-copy">
        <strong>{item.title}</strong>
        {item.subtitle ? <small>{cleanCaption(item.subtitle)}</small> : null}
      </div>
      {item.value || item.caption || item.detailPath || templateId ? (
        <span className={`list-trailing ${templateId ? 'has-action' : ''}`}>
          {item.value || item.caption ? (
            <span className="list-meta-pair">
              {item.value ? <b>{cleanCaption(item.value)}</b> : null}
              {item.caption ? <em>{cleanCaption(item.caption)}</em> : null}
            </span>
          ) : null}
          <span className="list-action-row">
            {templateId ? <em className="list-action-label">추가</em> : null}
            {item.detailPath || templateId ? <Chevron /> : null}
          </span>
        </span>
      ) : null}
    </button>
  )
}

async function handleListItemClick(item: AppItem, navigate: Navigate, templateId: string | null) {
  if (templateId) {
    const result = await api.addAppMissionFromTemplate(templateId)
    navigate(result.nextPath)
    return
  }
  if (item.detailPath) {
    navigate(item.detailPath)
  }
}

function templateIdFromItem(item: AppItem): string | null {
  const value = item.data?.templateId
  return typeof value === 'string' && value.length > 0 ? value : null
}

function compareFiltersFromItem(item: AppItem): AppCompareSearchRequest | null {
  if (!item.data) {
    return null
  }
  const hasCompareFilter =
    'ageBand' in item.data ||
    'incomeBand' in item.data ||
    'jobCategory' in item.data ||
    'moneyStyle' in item.data ||
    'area' in item.data ||
    'householdType' in item.data ||
    'assetRange' in item.data
  if (!hasCompareFilter) {
    return null
  }
  return {
    ageBand: stringFromData(item.data, 'ageBand') ?? '전체',
    incomeBand: stringFromData(item.data, 'incomeBand') ?? '전체',
    jobCategory: stringFromData(item.data, 'jobCategory') ?? '전체',
    moneyStyle: stringFromData(item.data, 'moneyStyle') ?? '전체',
    area: stringFromData(item.data, 'area') ?? '전체',
    householdType: stringFromData(item.data, 'householdType') ?? '전체',
    assetRange: stringFromData(item.data, 'assetRange') ?? '전체',
  }
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
  const handleClick = async () => {
    if (action.method === 'GET') {
      navigate(action.path)
      return
    }
    if (action.intent === 'birthday-open') {
      await api.openMyBirthdayFund()
      navigate('/birthday-funds/me/status')
      return
    }
    if (action.intent === 'birthday-share') {
      await api.shareMyBirthdayFund()
      navigate('/birthday-funds/me/status')
      return
    }
    if (action.intent === 'mission-add') {
      const templateId = action.path.split('/').filter(Boolean).at(-1)
      if (templateId) {
        const result = await api.addAppMissionFromTemplate(templateId)
        navigate(result.nextPath)
      }
      return
    }
    if (action.intent === 'logout') {
      await api.logout()
      clearSession()
      navigate('/login')
      return
    }
    navigate(action.path)
  }

  return (
    <button className={`app-button ${action.tone}`} type="button" onClick={() => { void handleClick() }}>
      {action.label}
    </button>
  )
}

function goDetail(section: AppSection, navigate: Navigate) {
  if (section.detailPath) {
    navigate(section.detailPath)
  }
}

function screenClass(screenId: string) {
  return `screen-${screenId.replace(/[^a-z0-9]+/gi, '-')}`
}

function cleanCaption(caption: string) {
  if (/RULE_BASED|FALLBACK|SOURCE|DEMO|MOCK/i.test(caption)) {
    return '코치 분석'
  }
  return caption
}

function stringFromData(data: Record<string, unknown> | null | undefined, key: string): string | null {
  const value = data?.[key]
  return typeof value === 'string' && value.length > 0 ? value : null
}

function clampPercent(value: number): number {
  return Math.max(0, Math.min(100, value))
}

function inferItemPresentation(item: AppItem, variant?: string): { icon: string; tone: string } {
  if (variant === 'feed' || item.icon === 'feed') {
    const text = `${item.title} ${item.subtitle}`.toLowerCase()
    if (text.includes('펀드') || text.includes('생일')) {
      return { icon: 'gift', tone: 'green' }
    }
    if (text.includes('저축') || text.includes('비상금')) {
      return { icon: 'saving', tone: 'green' }
    }
    if (text.includes('투자') || text.includes('주식')) {
      return { icon: 'stocks', tone: 'purple' }
    }
    if (text.includes('지출') || text.includes('카페')) {
      return { icon: 'spend', tone: 'warning' }
    }
    return { icon: 'check', tone: 'green' }
  }
  return { icon: (item.icon ?? 'check') as IconName, tone: item.tone ?? 'purple' }
}
