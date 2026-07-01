import type { ReactNode } from 'react'
import { api } from './api'
import { clearSession } from './session'
import type { AppAction, AppItem, AppMetric, AppScreenResponse, AppSection } from './types'
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

  return (
    <div className={`screen screen-${screen.tab}`}>
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
  return (
    <Card section={section} navigate={navigate} className={isScore ? 'score-card' : undefined}>
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
    <Card section={section} navigate={navigate} className="asset-card">
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

function ListSection({ section, navigate }: SectionProps) {
  return (
    <Card section={section} navigate={navigate} className={section.kind === 'feed' ? 'feed-card' : undefined}>
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
      {item.value || item.caption || item.detailPath ? (
        <span className="list-trailing">
          {item.value ? <b>{item.value}</b> : null}
          {item.caption ? <em>{item.caption}</em> : null}
          {item.detailPath ? <Chevron /> : null}
        </span>
      ) : null}
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
    if (action.intent === 'mission-feedback') {
      navigate(action.path)
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
