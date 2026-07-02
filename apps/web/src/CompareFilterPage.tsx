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

  if (state.status === 'loading') {
    return <LoadingScreen />
  }
  if (state.status === 'error') {
    return <ErrorScreen message={state.message} navigate={navigate} />
  }

  const resultCount = numberFromMeta(state.screen.meta.resultCount)
  const profiles = state.screen.sections.find((section) => section.id === 'profiles')?.items ?? []

  const updateFilter = async (key: FilterKey) => {
    const values = state.options[key] ?? ['전체']
    const current = state.filters[key]
    const index = Math.max(0, values.indexOf(current))
    const nextFilters = { ...state.filters, [key]: values[(index + 1) % values.length] }
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
          <button type="button" onClick={() => { void updateFilter(filter.key) }} key={filter.key}>
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
    </div>
  )
}

function CompareProfileCard({ item }: { item: AppItem }) {
  const stock = item.data?.stockSignal === true
  const saving = item.data?.savingSignal === true
  const pension = item.data?.pensionSignal === true

  return (
    <article className="compare-profile-card">
      <IconBadge icon="profile" tone="purple" />
      <div className="compare-profile-copy">
        <strong>{item.title}</strong>
        {item.subtitle ? <span>{item.subtitle}</span> : null}
        {item.caption ? <small>{item.caption}</small> : null}
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
      {label}
    </span>
  )
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

function stringValue(value: unknown): string {
  return typeof value === 'string' && value.length > 0 ? value : '전체'
}

function numberFromMeta(value: unknown): number {
  return typeof value === 'number' ? value : 0
}
