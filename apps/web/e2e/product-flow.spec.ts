import { expect, test, type Page } from '@playwright/test'

const apiUrl = process.env.PLAYWRIGHT_API_URL ?? 'http://localhost:8080'

test('starter signup state and seeded birthday fund product flow work end to end', async ({ context, page, request }) => {
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
  await expect(page.getByRole('heading', { name: '나와 비슷한 사람들의 금융 루틴을 비교해보세요' })).toBeVisible()

  const email = `e2e-${Date.now()}@finmate.local`
  await page.getByRole('textbox', { name: '이름' }).fill('새사용자')
  await page.getByRole('textbox', { name: '이메일' }).fill(email)
  await page.getByRole('textbox', { name: '비밀번호' }).fill('password123!')
  await page.getByRole('button', { name: '회원가입' }).click()

  await expect(page).toHaveURL(/\/onboarding/)
  await expect(page.getByRole('heading', { name: '30초 설문' })).toBeVisible()
  await page.getByRole('button', { name: '다음' }).click()
  await expect(page.getByRole('heading', { name: '개인정보 공개 동의' })).toBeVisible()
  await page.locator('label').filter({ hasText: '익명 비교와 친구 피드 공개 범위에 동의해요' }).click()
  await page.getByRole('button', { name: '다음' }).click()
  await expect(page.getByRole('heading', { name: '마이데이터 제공 동의' })).toBeVisible()
  await page.locator('label').filter({ hasText: '선택한 범위의 합성 금융 데이터를 FinMate 분석에 사용하는 데 동의해요' }).click()
  await page.getByRole('button', { name: '다음' }).click()
  await expect(page.getByRole('heading', { name: '준비가 끝났어요' })).toBeVisible()
  await page.getByRole('button', { name: 'FinMate 시작하기' }).click()

  await expect(page).toHaveURL(/\/home/)
  await expect(page.getByRole('heading', { name: /새사용자님, 좋은 아침이에요/ })).toBeVisible()
  await expect(page.getByText('내일 식비 10,000원 이하 사용하기')).toBeVisible()
  await expect(page.getByText('오늘의 예산')).toBeVisible()
  await expect(page.getByText('오늘의 지출 요약')).toBeVisible()
  await expect(page.getByText('자산 현황')).toBeVisible()
  await expect(page.getByText('아직 팔로잉한 친구가 없어요')).toBeVisible()
  await expect(page.getByRole('button', { name: '축하 펀드 참여하기' })).toHaveCount(0)
  await expectNoTechnicalCopy(page)
  await expectBottomTabs(page)

  await page.reload()
  await expect(page.getByRole('heading', { name: /새사용자님, 좋은 아침이에요/ })).toBeVisible()

  await page.getByRole('button', { name: '비교', exact: true }).click()
  await expect(page).toHaveURL(/\/compare/)
  await expect(page.getByRole('heading', { name: '금융 점수' })).toBeVisible()
  await page.getByRole('button', { name: 'AI 코치의 분석 보기' }).click()
  await expect(page).toHaveURL(/\/compare\/coach/)
  await expect(page.getByRole('heading', { name: 'AI 코치 분석' })).toBeVisible()

  await page.getByRole('button', { name: '미션', exact: true }).click()
  await expect(page).toHaveURL(/\/missions/)
  await page.getByRole('button', { name: '미션 추가하기' }).click()
  await expect(page).toHaveURL(/\/missions\/add/)
  await expect(page.getByRole('heading', { name: '추천 미션' })).toBeVisible()
  await page.getByRole('button', { name: '첫 추천 미션 추가하기' }).click()
  await expect(page).toHaveURL(/\/missions\/mission-auto-transfer-small/)
  await expect(page.getByRole('heading', { name: '이번 달 비상금 자동이체 10만 원 설정하기' })).toBeVisible()
  await page.getByRole('button', { name: '미션', exact: true }).click()
  await expect(page).toHaveURL(/\/missions/)
  await page.getByRole('button', { name: /오늘 실천 기록하기/ }).click()
  await expect(page).toHaveURL(/\/missions\/.+\/feedback/)
  await expect(page.locator('body')).toContainText(/\+\d+P/)
  await page.getByRole('button', { name: '기록 완료' }).click()
  await expect(page).toHaveURL(/\/records\/history/)
  await expect(page.getByRole('heading', { name: '월간 히스토리' })).toBeVisible()

  await page.getByRole('button', { name: '프로필' }).click()
  await expect(page).toHaveURL(/\/profile/)
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login/)

  const bootstrap = await request.post(`${apiUrl}/api/dev/bootstrap-test-account`, {
    data: {
      email: 'qa-birthday@finmate.local',
      password: 'password123!',
      displayName: '테스트 사용자',
      includeBirthdayEvent: true,
    },
  })
  expect(bootstrap.ok()).toBeTruthy()

  await page.getByRole('textbox', { name: '이메일' }).fill('qa-birthday@finmate.local')
  await page.getByRole('textbox', { name: '비밀번호' }).fill('password123!')
  await page.getByRole('button', { name: '로그인' }).click()
  await expect(page).toHaveURL(/\/home/)
  await expect(page.getByText('내일 식비 10,000원 이하 사용하기')).toBeVisible()
  await expect(page.getByRole('button', { name: '축하 펀드 참여하기' })).toBeVisible()

  await page.getByRole('button', { name: '미션', exact: true }).click()
  await expect(page).toHaveURL(/\/missions/)
  await page.getByRole('button', { name: /오늘 실천 기록하기/ }).click()
  await expect(page).toHaveURL(/\/missions\/.+\/feedback/)
  await expect(page.locator('body')).toContainText(/\+\d+P/)
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
  const profileSummary = page.locator('article').filter({ hasText: '테스트 사용자님의 금융 생활' })
  await expect(profileSummary).toContainText('2570P')
  await expect(profileSummary).toContainText('₩90,000')
  await expectNoTechnicalCopy(page)

  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByRole('heading', { name: '금융 루틴으로 다시 들어가기' })).toBeVisible()

  await page.goto('/home')
  await expect(page.getByRole('heading', { name: '금융 루틴으로 다시 들어가기' })).toBeVisible()
})

async function expectBottomTabs(page: Page) {
  for (const label of ['홈', '비교', '미션', '기록', '프로필']) {
    await expect(page.getByRole('button', { name: label, exact: true })).toBeVisible()
  }
}

async function expectNoTechnicalCopy(page: Page) {
  await expect(page.locator('body')).not.toContainText(/demo-token|P0|P1|HTTP \d|200 OK|410|mock/i)
}
