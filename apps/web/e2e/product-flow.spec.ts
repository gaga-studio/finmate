import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const apiUrl = process.env.PLAYWRIGHT_API_URL ?? 'http://localhost:8080'

test('p001 synthetic product flow and starter signup flow work end to end', async ({ context, page, request }) => {
  const reset = await request.post(`${apiUrl}/api/dev/reset`)
  expect(reset.ok()).toBeTruthy()
  await importSyntheticFixture(request)

  await context.clearCookies()
  await page.goto('/login')
  await expect(page.getByRole('textbox', { name: '이메일' })).toHaveValue('p001@synthetic.finmate.local')
  await expect(page.getByRole('textbox', { name: '비밀번호' })).toHaveValue('password123!')
  await page.getByRole('button', { name: '로그인' }).click()

  await expect(page).toHaveURL(/\/home/)
  await expect(page.getByText('오늘의 예산')).toBeVisible()
  await expect(page.getByText('오늘의 지출 요약')).toBeVisible()
  await expect(page.getByRole('button', { name: '축하 펀드 참여하기' })).toBeVisible()
  await expectNoTechnicalCopy(page)
  await expectBottomTabs(page)

  await page.getByRole('button', { name: '비교', exact: true }).click()
  await expect(page).toHaveURL(/\/compare/)
  await expect(page.getByText('AI 추천 그룹')).toBeVisible()
  await expect(page.getByText('내 그룹 비교')).toBeVisible()
  await page.getByRole('button', { name: /직접 만들기/ }).first().click()
  await expect(page).toHaveURL(/\/compare\/filter/)
  await expect(page.getByRole('heading', { name: '필터링 조회' })).toBeVisible()
  await page.getByRole('button', { name: /업종/ }).click()
  await expect(page.getByText('검색 결과 1명')).toBeVisible()
  await expect(page.getByText('서연')).toBeVisible()
  await page.getByRole('button', { name: /이 조건으로 비교하기/ }).click()
  await expect(page).toHaveURL(/\/compare\/results\/cmp-/)
  await expect(page.getByText('나의 금융 점수')).toBeVisible()
  await expect(page.getByText('비교 그룹 평균', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '비교', exact: true }).click()
  await expect(page.getByText('나 vs 20대 디자인 평균')).toBeVisible()

  await page.getByRole('button', { name: '프로필', exact: true }).click()
  await expect(page).toHaveURL(/\/profile/)
  await expect(page.getByRole('heading', { name: '팔로잉 금융 현황' })).toBeVisible()
  await expect(page.getByText('내 팔로잉 1명의 최근 금융 활동이에요')).toBeVisible()

  await page.getByRole('button', { name: '미션', exact: true }).click()
  await expect(page).toHaveURL(/\/missions/)
  await expect(page.getByRole('button', { name: /오늘 실천 기록하기/ })).toHaveCount(0)
  await page.getByText('고정 지출 5% 줄이기').click()
  await expect(page.getByRole('heading', { name: '완료 조건과 근거' })).toBeVisible()
  await expect(page.getByText(/데이터가 더 필요/)).toBeVisible()
  await expect(page.getByText('평가 기간')).toBeVisible()

  await page.getByRole('button', { name: '미션', exact: true }).click()
  await page.getByRole('button', { name: '미션 추가하기' }).click()
  await expect(page).toHaveURL(/\/missions\/add/)
  await expect(page.getByRole('button', { name: /첫 추천 미션 추가하기/ })).toHaveCount(0)
  await page.getByRole('button', { name: /이번 주 카페 2회 이하 이용하기/ }).click()
  await expect(page).toHaveURL(/\/missions\/mission-cafe-limit-weekly/)
  await expect(page.getByRole('heading', { name: '완료 조건과 근거' })).toBeVisible()
  await expect(page.getByText(/미션 추가 이후 새 행동 데이터가 아직 없어요/)).toBeVisible()
  await expect(page.getByText('평가 기간')).toBeVisible()

  await page.getByRole('button', { name: '기록', exact: true }).click()
  await expect(page).toHaveURL(/\/records/)
  await expect(page.getByRole('heading', { name: '기록', exact: true })).toBeVisible()
  await expect(page.getByText('추가됨')).toHaveCount(0)

  await page.getByRole('button', { name: '홈' }).click()
  await page.getByRole('button', { name: '축하 펀드 참여하기' }).click()
  await expect(page).toHaveURL(/\/birthday-funds\/fund-p002-birthday\/contribute/)
  await page.getByRole('button', { name: '다음' }).click()
  await expect(page).toHaveURL(/\/birthday-funds\/fund-p002-birthday\/complete/)
  await expect(page.getByText('₩82,000')).toBeVisible()

  await page.getByRole('button', { name: '프로필' }).click()
  await expect(page).toHaveURL(/\/profile/)
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login/)

  await page.getByRole('button', { name: '처음이라면 회원가입' }).click()
  await expect(page).toHaveURL(/\/signup/)
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
  await expect(page.getByText('오늘의 예산')).toBeVisible()
  await expect(page.getByText('오늘의 지출 요약')).toBeVisible()
  await expect(page.getByText('아직 팔로잉한 친구가 없어요')).toBeVisible()
  await expect(page.getByRole('button', { name: '축하 펀드 참여하기' })).toHaveCount(0)

  await page.getByRole('button', { name: '미션', exact: true }).click()
  await page.getByRole('button', { name: '미션 추가하기' }).click()
  await expect(page).toHaveURL(/\/missions\/add/)
  await expect(page.getByRole('heading', { name: '추천 미션' })).toBeVisible()
  await expect(page.getByRole('button', { name: /첫 추천 미션 추가하기/ })).toHaveCount(0)
  await expectNoTechnicalCopy(page)

  await page.getByRole('button', { name: '프로필' }).click()
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login/)

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

async function importSyntheticFixture(request: APIRequestContext) {
  const response = await request.post(`${apiUrl}/api/dev/import-synthetic-dataset`, {
    data: {
      importVersion: 'e2e-import',
      sourceRepository: 'https://github.com/gaga-studio/financial-sns-mydata-202606',
      sourceCommit: 'e2e',
      resetSynthetic: true,
      users: [syntheticUser('P001', '하민', ['P002'], true), syntheticUser('P002', '서연', [], false, {
        fundId: 'fund-p002-birthday',
        ownerPersonaId: 'P002',
        title: '서연님의 생일 펀드',
        targetAmount: 100000,
        currentAmount: 72000,
        dueDate: '2026-06-15',
        status: 'OPEN',
        shareCode: 'SYNTH-BDAY-E2E',
      })],
    },
  })
  expect(response.ok()).toBeTruthy()
}

function syntheticUser(
  personaId: 'P001' | 'P002',
  displayName: string,
  follows: string[],
  includeBirthdayFeed: boolean,
  birthdayFund: Record<string, unknown> | null = null,
) {
  const isP001 = personaId === 'P001'
  return {
    personaId,
    email: `${personaId.toLowerCase()}@synthetic.finmate.local`,
    password: 'password123!',
    displayName,
    profile: {
      ageBand: '20대 후반',
      incomeBand: '3,000만원 ~ 4,000만원',
      jobCategory: isP001 ? 'IT/개발' : '디자인',
      householdType: '1인가구',
      moneyStyle: '안정 추구형',
      area: '서울',
      goalType: isP001 ? 'EMERGENCY_FUND' : 'SAVING',
      painPoint: 'SAVE_CONSISTENTLY',
    },
    privacy: {
      anonymousPortfolioOptIn: true,
      friendShareDefault: 'MISSION_ONLY',
      exposedFields: ['ageBand', 'goalType', 'financialSummary', 'missionStatus'],
    },
    mydata: {
      consentVersion: 'synthetic-mydata-e2e',
      scopes: ['ACCOUNT_SUMMARY', 'CARD_SPENDING', 'INVESTMENT_SUMMARY'],
    },
    wallet: { pointBalance: 0, virtualMoneyBalance: 100000 },
    snapshot: {
      month: '2026-06',
      monthlyIncome: isP001 ? 2900000 : 2800000,
      monthlySpending: isP001 ? 1600000 : 1500000,
      monthlySaving: isP001 ? 180000 : 140000,
      investmentValue: isP001 ? 250000 : 100000,
      cashLikeAssets: isP001 ? 500000 : 420000,
      emergencyFundMonths: isP001 ? 0.31 : 0.28,
      spendingCategories: { 식비: 240000, 교통비: 90000, '카페/간식': 50000, 기타: 1220000 },
      lifestyleTags: ['비상금', '루틴'],
    },
    dailyRecords: [{
      recordDate: '2026-06-12',
      budget: 10000,
      spent: isP001 ? 7800 : 9000,
      categorySpending: { 식비: isP001 ? 7800 : 9000, 교통비: 0, '카페/간식': 0, 기타: 0 },
      missionStatus: 'IN_PROGRESS',
      pointDelta: 0,
    }],
    transactions: [{
      transactionId: `${personaId}-T001`,
      transactionDate: '2026-06-12',
      transactionTime: '12:10',
      transactionType: '지출',
      category: '식비',
      subcategory: '점심',
      description: '점심',
      amountKrw: isP001 ? -7800 : -9000,
      cashflowBucket: '소비',
      accountRef: `bank:${personaId}`,
      apiRef: `card:${personaId}`,
    }, {
      transactionId: `${personaId}-T002`,
      transactionDate: '2026-06-03',
      transactionTime: '09:00',
      transactionType: '저축',
      category: '저축',
      subcategory: '비상금',
      description: '비상금 자동이체',
      amountKrw: isP001 ? -120000 : -100000,
      cashflowBucket: '저축',
      accountRef: `bank:${personaId}`,
      apiRef: `bank:${personaId}`,
    }],
    missions: [{
      missionId: 'mission-food',
      title: '내일 식비 10,000원 이하 사용하기',
      description: '하루 식비 10,000원 이하',
      status: 'ACTIVE',
      difficulty: 'EASY',
      rewardPoints: 120,
      progress: 40,
      source: 'SYNTHETIC_MYDATA_IMPORT',
    }, {
      missionId: 'mission-saving',
      title: '저축하기 습관 만들기',
      description: '비상금 100,000원 이상 저축',
      status: 'ACTIVE',
      difficulty: 'EASY',
      rewardPoints: 200,
      progress: 40,
      source: 'SYNTHETIC_MYDATA_IMPORT',
    }, {
      missionId: 'mission-fixed-cost',
      title: '고정 지출 5% 줄이기',
      description: '구독과 반복 결제 점검',
      status: 'ACTIVE',
      difficulty: 'NORMAL',
      rewardPoints: 180,
      progress: 20,
      source: 'SYNTHETIC_MYDATA_IMPORT',
    }],
    follows,
    feedItems: includeBirthdayFeed ? [{
      feedId: 'feed-P001-birthday',
      actorPersonaId: 'P002',
      kind: 'BIRTHDAY',
      title: '서연님의 생일 펀드가 열렸어요',
      body: '친구들이 함께 모으는 생일 축하 펀드',
      amount: 72000,
    }] : [],
    birthdayFund,
  }
}
