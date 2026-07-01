import { useEffect, useState } from 'react'
import { api } from './api'
import { clearSession } from './session'
import type { Route, Navigate } from './navigation'
import type { AppScreenResponse } from './types'
import { describeError, isUnauthorized } from './errors'
import { ErrorScreen, LoadingScreen, ScreenRenderer } from './screenRenderer'

type LoadState =
  | { status: 'loading' }
  | { status: 'success'; screen: AppScreenResponse }
  | { status: 'error'; message: string }

export function AppScreenPage({
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
          if (isUnauthorized(error)) {
            clearSession()
            return
          }
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
