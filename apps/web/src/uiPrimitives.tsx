import { useMemo } from 'react'
import type { Navigate, TabKey } from './navigation'

export type IconName =
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

const tabItems: Array<{ key: TabKey; label: string; icon: IconName; path: string }> = [
  { key: 'home', label: '홈', icon: 'home', path: '/home' },
  { key: 'compare', label: '비교', icon: 'search', path: '/compare' },
  { key: 'mission', label: '미션', icon: 'check-square', path: '/missions' },
  { key: 'records', label: '기록', icon: 'calendar', path: '/records' },
  { key: 'profile', label: '프로필', icon: 'profile', path: '/profile' },
]

export function StatusBar({ time }: { time: string }) {
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

export function BottomNav({ active, navigate }: { active: TabKey; navigate: Navigate }) {
  return (
    <nav className="bottom-nav" aria-label="주요 메뉴">
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

export function IconButton({ icon, label, onClick }: { icon: IconName; label: string; onClick?: () => void }) {
  return (
    <button className="icon-button" type="button" aria-label={label} onClick={onClick}>
      <AppIcon name={icon} />
    </button>
  )
}

export function IconBadge({ icon, tone }: { icon: string; tone: string }) {
  return (
    <span className={`icon-badge ${tone}`}>
      <AppIcon name={toIconName(icon)} />
    </span>
  )
}

export function RingProgress({ value, label }: { value: number; label: string }) {
  const deg = Math.max(0, Math.min(100, value)) * 3.6
  return (
    <div className="ring" style={{ background: `conic-gradient(var(--color-primary) ${deg}deg, var(--color-track) 0deg)` }}>
      <div>
        <strong>{value}%</strong>
        <span>{label}</span>
      </div>
    </div>
  )
}

export function ProgressLine({ value, tone }: { value: number; tone: 'purple' | 'green' | 'gray' }) {
  return (
    <span className={`progress-line ${tone}`}>
      <i style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
    </span>
  )
}

export function MiniLineChart({ values }: { values: number[] }) {
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
      <polyline points={points} fill="none" stroke="var(--color-primary)" strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />
    </svg>
  )
}

export function Chevron({ direction = 'right' }: { direction?: 'right' | 'left' }) {
  return <AppIcon name={direction === 'left' ? 'chevron-left' : 'chevron-right'} />
}

export function Legend({ tone, label }: { tone: string; label: string }) {
  return (
    <span className="legend">
      <i className={tone} />
      {label}
    </span>
  )
}

function toIconName(icon: string): IconName {
  const iconMap: Record<string, IconName> = {
    'check-square': 'check-square',
    'avatar-j': 'profile',
    'avatar-m': 'profile',
    'avatar-t': 'profile',
    feed: 'check',
    lock: 'settings',
    records: 'calendar',
    target: 'check-square',
    wallet: 'saving',
    work: 'profile',
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

export function AppIcon({ name }: { name: IconName }) {
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
