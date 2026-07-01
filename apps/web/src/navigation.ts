export type Navigate = (path: string) => void

export type TabKey = 'home' | 'compare' | 'mission' | 'records' | 'profile'

export type ScreenKey =
  | 'home'
  | 'home-detail'
  | 'compare'
  | 'compare-filter'
  | 'compare-results'
  | 'compare-result'
  | 'compare-coach'
  | 'missions'
  | 'mission-add'
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

export type Route =
  | { name: 'login' }
  | { name: 'signup' }
  | { name: 'onboarding' }
  | { name: 'screen'; screen: ScreenKey; param?: string }
  | { name: 'birthday-contribution'; fundId: string }
  | { name: 'mission-feedback'; missionId: string }
  | { name: 'not-found' }

export function parseRoute(pathname: string): Route {
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
    if (parts[1] === 'new' || parts[1] === 'add') {
      return { name: 'screen', screen: 'mission-add', param: parts[2] }
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

export function getActiveTab(route: Route): TabKey {
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
