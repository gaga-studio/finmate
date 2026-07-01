import { useState } from 'react'
import { api } from './api'
import type { Navigate } from './navigation'
import { clearSession, type FinMateSession } from './session'
import { describeError } from './errors'
import { IconBadge, StatusBar } from './uiPrimitives'

export function AuthPage({
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
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
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

  if (!isSignup && session.accessToken && session.user) {
    return (
      <div className="screen auth-screen">
        <StatusBar time="9:41" />
        <section className="auth-hero returning">
          <IconBadge icon="check" tone="purple" />
          <img src="/assets/characters/finmate-main.png" alt="" />
          <h1>{session.user.displayName}님, 금융 루틴을 이어갈까요?</h1>
          <p>미션, 기록, 포인트, 친구 금융 생활이 이 계정에 저장되어 있어요.</p>
        </section>
        <div className="auth-actions">
          <button className="app-button primary" type="button" onClick={() => navigate(session.user?.onboardingCompleted ? '/home' : '/onboarding')}>앱으로 돌아가기</button>
          <button className="app-button secondary" type="button" onClick={() => { clearSession(); navigate('/login') }}>다른 계정으로 로그인</button>
        </div>
      </div>
    )
  }

  return (
    <div className="screen auth-screen">
      <StatusBar time="9:41" />
      <section className="auth-hero">
        <span className="surface-label">FinMate</span>
        <img src="/assets/characters/finmate-main.png" alt="" />
        <h1>{isSignup ? '나와 비슷한 사람들의 금융 루틴을 비교해보세요' : '금융 루틴으로 다시 들어가기'}</h1>
        <p>{isSignup ? '계정 하나로 온보딩, 미션, 기록, 친구 피드, 생일펀드를 안전하게 관리해요.' : '오늘의 예산과 미션, 친구들의 금융 근황을 바로 확인할 수 있어요.'}</p>
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
