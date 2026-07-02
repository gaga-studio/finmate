import { useEffect, useState } from 'react'
import { api } from './api'
import { describeError } from './errors'
import type { Navigate } from './navigation'
import type { AppCompareSearchRequest, AppItem, AppScreenResponse } from './types'
import { Chevron, IconBadge, IconButton, StatusBar } from './uiPrimitives'
import { ErrorScreen, LoadingScreen } from './screenRenderer'

type FilterKey = 'jobCategory' | 'incomeBand' | 'ageBand' | 'moneyStyle' | 'area'

type LoadState =
  | { status: 'loading' }
  | { status: 'ready'; screen: AppScreenResponse; filters: AppCompareSearchRequest; options: Record<string, string[]>; notice?: string }
  | { status: 'error'; message: string }

const filterOrder: Array<{ key: FilterKey; label: string }> = [
  { key: 'jobCategory', label: '업종' },
  { key: 'incomeBand', label: '연소득' },
  { key: 'ageBand', label: '나이' },
  { key: 'moneyStyle', label: '성향' },
  { key: 'area', label: '생활권' },
]

const fallbackFilters: AppCompareSearchRequest = {
  ageBand: '전체',
  incomeBand: '전체',
  jobCategory: '전체',
  moneyStyle: '전체',
  area: '전체',
  householdType: '전체',
  assetRange: '전체',
}

export function CompareFilterPage({ navigate }: { navigate: Navigate }) {
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [activeFilter, setActiveFilter] = useState<FilterKey | null>(null)

  useEffect(() => {
    let active = true
    api.getAppCompareFilter()
      .then((screen) => {
        if (!active) {
          return
        }
        setState({
          status: 'ready',
          screen,
          filters: filtersFromMeta(screen),
          options: optionsFromMeta(screen),
        })
      })
      .catch((error: unknown) => {
        if (active) {
          setState({ status: 'error', message: describeError(error) })
        }
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    if (!activeFilter) {
      return undefined
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setActiveFilter(null)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [activeFilter])

  if (state.status === 'loading') {
    return <LoadingScreen />
  }
  if (state.status === 'error') {
    return <ErrorScreen message={state.message} navigate={navigate} />
  }

  const resultCount = numberFromMeta(state.screen.meta.resultCount)
  const profiles = state.screen.sections.find((section) => section.id === 'profiles')?.items ?? []
  const activeFilterConfig = activeFilter ? filterOrder.find((filter) => filter.key === activeFilter) : undefined

  const selectFilterValue = async (key: FilterKey, value: string) => {
    const nextFilters = { ...state.filters, [key]: value }
    setActiveFilter(null)
    setState({ ...state, filters: nextFilters, notice: undefined })
    try {
      const screen = await api.searchAppCompareFilter(nextFilters)
      setState({ status: 'ready', screen, filters: nextFilters, options: optionsFromMeta(screen), notice: undefined })
    } catch (error: unknown) {
      setState({ ...state, filters: nextFilters, notice: describeError(error) })
    }
  }

  const createGroup = async () => {
    try {
      const result = await api.createAppCompareGroup(state.filters)
      if (result.status === 'CREATED') {
        navigate(result.nextPath)
        return
      }
      setState({ ...state, notice: result.message })
    } catch (error: unknown) {
      setState({ ...state, notice: describeError(error) })
    }
  }

  return (
    <div className="screen screen-compare screen-compare-filter">
      <StatusBar time={state.screen.statusBarTime} />
      <header className="app-header">
        <div className="header-side">
          <IconButton icon="back" label="뒤로" onClick={() => navigate('/compare')} />
        </div>
        <h1>필터링 조회</h1>
        <div className="header-side right">
          <IconButton icon="sliders" label="필터" />
        </div>
      </header>

      <section className="compare-filter-chips" aria-label="비교 필터">
        {filterOrder.map((filter) => (
          <button
            className={activeFilter === filter.key ? 'is-open' : ''}
            type="button"
            aria-haspopup="dialog"
            aria-expanded={activeFilter === filter.key}
            onClick={() => setActiveFilter(filter.key)}
            key={filter.key}
          >
            <span>{filter.label}</span>
            <strong>{state.filters[filter.key]}</strong>
            <Chevron />
          </button>
        ))}
      </section>

      <section className="compare-filter-results">
        <p className="compare-result-count">검색 결과 {resultCount}명</p>
        <div className="compare-profile-list">
          {profiles.map((item) => (
            <CompareProfileCard item={item} key={item.id} />
          ))}
        </div>
      </section>

      {state.notice ? <p className="inline-notice">{state.notice}</p> : null}
      <div className="compare-filter-action">
        <button className="app-button primary" type="button" onClick={() => { void createGroup() }}>
          이 조건으로 비교하기 ({resultCount}명)
        </button>
      </div>

      {activeFilterConfig ? (
        <FilterBottomSheet
          filter={activeFilterConfig}
          currentValue={state.filters[activeFilterConfig.key]}
          values={filterOptionsFor(state.options, activeFilterConfig.key)}
          onClose={() => setActiveFilter(null)}
          onSelect={(value) => { void selectFilterValue(activeFilterConfig.key, value) }}
        />
      ) : null}
    </div>
  )
}

function FilterBottomSheet({
  filter,
  currentValue,
  values,
  onClose,
  onSelect,
}: {
  filter: { key: FilterKey; label: string }
  currentValue: string
  values: string[]
  onClose: () => void
  onSelect: (value: string) => void
}) {
  const titleId = `filter-sheet-title-${filter.key}`

  return (
    <div className="filter-sheet" role="presentation">
      <button className="filter-sheet-backdrop" type="button" aria-label="필터 선택 닫기" onClick={onClose} />
      <section className="filter-sheet-panel" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <div className="filter-sheet-handle" aria-hidden="true" />
        <header className="filter-sheet-header">
          <h2 id={titleId}>{filter.label} 선택</h2>
          <button className="filter-sheet-close" type="button" onClick={onClose}>닫기</button>
        </header>
        <div className="filter-sheet-options">
          {values.map((value) => {
            const selected = value === currentValue
            return (
              <button
                className={selected ? 'filter-sheet-option is-active' : 'filter-sheet-option'}
                type="button"
                aria-pressed={selected}
                onClick={() => onSelect(value)}
                key={value}
              >
                <span>{value}</span>
                {selected ? <IconBadge icon="check" tone="purple" /> : null}
              </button>
            )
          })}
        </div>
      </section>
    </div>
  )
}

function CompareProfileCard({ item }: { item: AppItem }) {
  const stock = item.data?.stockSignal === true
  const saving = item.data?.savingSignal === true
  const pension = item.data?.pensionSignal === true
  const ageBand = dataText(item, 'ageBand', '나이 미공개')
  const jobCategory = dataText(item, 'jobCategory', '직업 미공개')
  const incomeBand = dataText(item, 'incomeBand', '미공개')
  const area = dataText(item, 'area', '지역 미공개')
  const moneyStyle = dataText(item, 'moneyStyle', '성향 미공개')
  const tags = [
    moneyStyle !== '성향 미공개' ? moneyStyle : '',
    stock ? '투자중' : '',
    saving ? '저축중' : '',
    pension ? '연금준비' : '',
  ].filter(Boolean).slice(0, 2)

  return (
    <article className="compare-profile-card compare-filter-profile-card">
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
        <Signal active={stock} label="주식" icon="stocks" />
        <Signal active={saving} label="적금" icon="saving" />
        <Signal active={pension} label="연금" icon="pension" />
      </div>
    </article>
  )
}

function Signal({ active, label, icon }: { active: boolean; label: string; icon: string }) {
  return (
    <span className={active ? 'active' : ''}>
      <IconBadge icon={icon} tone={active ? 'purple' : 'muted'} />
      <b>{label}</b>
      <i aria-hidden="true" />
    </span>
  )
}

function dataText(item: AppItem, key: string, fallback: string): string {
  const value = item.data?.[key]
  return typeof value === 'string' && value.length > 0 ? value : fallback
}

function filtersFromMeta(screen: AppScreenResponse): AppCompareSearchRequest {
  const filters = screen.meta.filters
  if (!filters || typeof filters !== 'object') {
    return fallbackFilters
  }
  const map = filters as Record<string, unknown>
  return {
    ageBand: stringValue(map.ageBand),
    incomeBand: stringValue(map.incomeBand),
    jobCategory: stringValue(map.jobCategory),
    moneyStyle: stringValue(map.moneyStyle),
    area: stringValue(map.area),
    householdType: stringValue(map.householdType),
    assetRange: stringValue(map.assetRange),
  }
}

function optionsFromMeta(screen: AppScreenResponse): Record<string, string[]> {
  const raw = screen.meta.filterOptions
  if (!raw || typeof raw !== 'object') {
    return {}
  }
  return Object.fromEntries(
    Object.entries(raw as Record<string, unknown>).map(([key, value]) => [
      key,
      Array.isArray(value) ? value.map((item) => String(item)) : ['전체'],
    ]),
  )
}

function filterOptionsFor(options: Record<string, string[]>, key: FilterKey): string[] {
  const values = options[key] ?? []
  const uniqueValues = Array.from(new Set(['전체', ...values.filter((value) => value.length > 0)]))
  return uniqueValues.length > 0 ? uniqueValues : ['전체']
}

function stringValue(value: unknown): string {
  return typeof value === 'string' && value.length > 0 ? value : '전체'
}

function numberFromMeta(value: unknown): number {
  return typeof value === 'number' ? value : 0
}
