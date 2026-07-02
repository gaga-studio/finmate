import { useState } from 'react'
import { api } from './api'
import type { Navigate } from './navigation'
import { IconButton, StatusBar } from './uiPrimitives'

export function BirthdayContributionPage({ fundId, navigate }: { fundId: string; navigate: Navigate }) {
  const [amount, setAmount] = useState(10000)
  const [message, setMessage] = useState('생일 축하해!')
  const [anonymous, setAnonymous] = useState(false)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setBusy(true)
    try {
      await api.contributeBirthdayFund(fundId, { amount, message, anonymous })
      navigate(`/birthday-funds/${fundId}/complete`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="screen contribution-screen">
      <StatusBar time="9:41" />
      <header className="app-header">
        <div className="header-side"><IconButton icon="back" label="뒤로" onClick={() => navigate('/birthdays')} /></div>
        <h1>참여하기</h1>
        <div className="header-side right"><IconButton icon="bell" label="알림" /></div>
      </header>
      <section className="screen-stack">
        <article className="card form-card">
          <span className="surface-label">생일펀드</span>
          <h2>참여할 금액을 입력해주세요</h2>
          <div className="amount-display">₩{amount.toLocaleString('ko-KR')}</div>
          <div className="amount-options">
            {[5000, 10000, 20000].map((value) => (
              <button className={value === amount ? 'selected' : ''} type="button" onClick={() => setAmount(value)} key={value}>
                +{value.toLocaleString('ko-KR')}
              </button>
            ))}
          </div>
          <label className="field-label" htmlFor="birthday-message">축하 메시지</label>
          <textarea id="birthday-message" value={message} onChange={(event) => setMessage(event.target.value)} />
          <label className={`switch-row ${anonymous ? 'checked' : ''}`}>
            <span>
              익명으로 참여하기
              <small>친구 피드에는 이름 없이 금액만 표시돼요.</small>
            </span>
            <input type="checkbox" checked={anonymous} onChange={(event) => setAnonymous(event.target.checked)} />
            <i aria-hidden="true" />
          </label>
          <button className="app-button primary" type="button" disabled={busy} onClick={() => { void submit() }}>
            {busy ? '참여 중' : '다음'}
          </button>
        </article>
      </section>
    </div>
  )
}
