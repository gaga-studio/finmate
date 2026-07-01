import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from './api'
import { AuthPage } from './AuthPage'
import { AppScreenPage } from './AppScreenPage'
import { OnboardingPage } from './OnboardingPage'
import { BirthdayContributionPage, MissionFeedbackPage } from './SpecialScreens'
import { getActiveTab, parseRoute, type Navigate, type Route } from './navigation'
import { clearSession, getSession, saveSession, type FinMateSession } from './session'
import { BottomNav } from './uiPrimitives'
import { LoadingScreen, NotFoundPage } from './screenRenderer'
import './App.css'

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
      <AppShell>
        <LoadingScreen />
      </AppShell>
    )
  }

  if (route.name === 'login') {
    return <AppShell><AuthPage mode="login" onAuth={handleAuth} navigate={navigate} session={session} /></AppShell>
  }
  if (route.name === 'signup') {
    return <AppShell><AuthPage mode="signup" onAuth={handleAuth} navigate={navigate} session={session} /></AppShell>
  }
  if (!session.accessToken) {
    return <AppShell><AuthPage mode="login" onAuth={handleAuth} navigate={navigate} session={session} /></AppShell>
  }

  return (
    <AppShell>
      {renderRoute(route, pathname, navigate, session)}
      {route.name !== 'onboarding' ? <BottomNav active={activeTab} navigate={navigate} /> : null}
    </AppShell>
  )
}

function AppShell({ children }: { children: ReactNode }) {
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

export default App
