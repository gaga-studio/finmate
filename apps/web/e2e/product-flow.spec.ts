import { expect, test, type Page } from '@playwright/test'

const apiUrl = process.env.PLAYWRIGHT_API_URL ?? 'http://localhost:8080'

test('signup to birthday fund product flow works end to end', async ({ context, page, request }) => {
  const reset = await request.post(`${apiUrl}/api/dev/reset`)
  expect(reset.ok()).toBeTruthy()

  await context.clearCookies()
  await page.goto('/login')
  await page.evaluate(() => {
    window.localStorage.setItem('finmate:session', JSON.stringify({
      accessToken: 'expired-access-token',
      expiresAt: '2020-01-01T00:00:00Z',
      user: {
        userId: 'stale-user',
        email: 'stale@finmate.local',
        displayName: '예전사용자',
        onboardingCompleted: true,
        pointBalance: 0,
        virtualMoneyBalance: 0,
      },
      canRefresh: true,
    }))
  })
  await page.goto('/signup')
  await expect(page.getByRole('heading', { name: 'FinMate 시작하기' })).toBeVisible()

  const email = `e2e-${Date.now()}@finmate.local`
  await page.getByRole('textbox', { name: '이름' }).fill('민준')
  await page.getByRole('textbox', { name: '이메일' }).fill(email)
  await page.getByRole('textbox', { name: '비밀번호' }).fill('password123!')
  await page.getByRole('button', { name: '회원가입' }).click()

  await expect(page).toHaveURL(/\/onboarding/)
  await page.getByRole('button', { name: '시작하기' }).click()

  await expect(page).toHaveURL(/\/home/)
  await expect(page.getByRole('heading', { name: /민준님, 좋은 아침이에요/ })).toBeVisible()
  await expectNoTechnicalCopy(page)
  await expectBottomTabs(page)

  await page.reload()
  await expect(page.getByRole('heading', { name: /민준님, 좋은 아침이에요/ })).toBeVisible()

  await page.getByRole('button', { name: /오늘 실천 기록하기/ }).click()
  await expect(page).toHaveURL(/\/missions\/mission-food\/feedback/)
  await expect(page.getByText('+120P')).toBeVisible()
  await expectNoTechnicalCopy(page)

  await page.getByRole('button', { name: '다음 목표 보기' }).click()
  await expect(page).toHaveURL(/\/missions\/next-goals/)
  await expect(page.getByRole('heading', { name: '다음 목표 제안' })).toBeVisible()

  await page.goto('/records/history')
  await expect(page).toHaveURL(/\/records\/history/)
  await expect(page.getByRole('heading', { name: '월간 히스토리' })).toBeVisible()

  await page.getByRole('button', { name: '홈' }).click()
  await expect(page).toHaveURL(/\/home/)
  await page.getByRole('button', { name: '축하 펀드 참여하기' }).click()
  await expect(page).toHaveURL(/\/birthday-funds\/fund-jiwoo\/contribute/)
  await page.getByRole('button', { name: '다음' }).click()
  await expect(page).toHaveURL(/\/birthday-funds\/fund-jiwoo\/complete/)
  await expect(page.getByText('₩82,000')).toBeVisible()

  await page.goto('/profile/points')
  await expect(page.getByRole('heading', { name: '포인트 내역' })).toBeVisible()
  await expect(page.locator('article').filter({ hasText: '포인트 지갑' }).getByText('₩90,000', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '프로필' }).click()
  await expect(page).toHaveURL(/\/profile/)
  const profileSummary = page.locator('article').filter({ hasText: '민준님의 금융 생활' })
  await expect(profileSummary).toContainText('2570P')
  await expect(profileSummary).toContainText('₩90,000')
  await expectNoTechnicalCopy(page)

  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByRole('heading', { name: 'FinMate 로그인' })).toBeVisible()

  await page.goto('/home')
  await expect(page.getByRole('heading', { name: 'FinMate 로그인' })).toBeVisible()
})

async function expectBottomTabs(page: Page) {
  for (const label of ['홈', '비교', '미션', '기록', '프로필']) {
    await expect(page.getByRole('button', { name: label, exact: true })).toBeVisible()
  }
}

async function expectNoTechnicalCopy(page: Page) {
  await expect(page.locator('body')).not.toContainText(/demo-token|P0|P1|HTTP \d|200 OK|410|mock/i)
}
