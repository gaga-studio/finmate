# FinMate 화면별 상태 명세서 v1.0

작성일: 2026-06-30  
용도: P0 화면이 API v1.0 response와 정확히 맞도록 route, 상태, validation, toast/modal 기준을 고정한다.

## 1. 화면/Route 요약

| 화면 ID | route | 목적 | 주요 API |
| --- | --- | --- | --- |
| ONB-02 | `/onboarding` | 30초 진단, mock 동의, 개인정보 동의, 세션 시작 | diagnosis, mock-consent, privacy-consents, demo-session |
| HOME-01 | `/home` | 내 금융 요약과 또래 teaser | `GET /api/home` |
| EXP-03 | `/explore/portfolios/:id` | 또래 포트폴리오 상세 | `GET /api/explore/portfolios/{id}` |
| EXP-04 | `/explore/compare/:portfolioId` | 1:1 비교와 mainGap | `POST /api/comparisons` |
| SIM-01 | `/simulations/:comparisonId` | 3개월 시뮬레이션 | `POST /api/simulations` |
| MIS-01 | `/missions/new/:simulationId` | 오늘의 미션 생성 | `POST /api/missions` |
| SET-01 | `/settings/privacy` | 공개 미리보기, 수정, 철회 | privacy APIs |

## 2. ONB-02 30초 진단/동의 흐름

| 항목 | 명세 |
| --- | --- |
| 목적 | 진단부터 `accessToken` 발급까지 ONB-02 내부 단계로 처리 |
| 사용자 액션 | 상태/소득/가구/목표/고민 선택, mock 마이데이터 동의, 개인정보 공개 범위 확인 |
| 호출 API | `POST /api/onboarding/diagnosis`, `POST /api/mydata/mock-consent`, `POST /api/privacy/consents`, `POST /api/demo/session` |
| 성공 상태 | `onboardingToken` 저장 후 동의 API 호출, 마지막에 `accessToken`, `selectedPersonaId`, `goalType` 저장 |
| 버튼 이동 | 완료 → `/home` |
| disabled 조건 | 필수 선택값 누락, 동의 체크박스 미선택, API loading 중 |
| form validation | `occupationStatus`, `incomeBand`, `householdType`, `goalType`, `painPoint` 필수 |
| toast | “진단이 완료됐어요.”, “동의가 저장됐어요.”, “데모 세션을 시작합니다.” |
| error copy | `VALIDATION_ERROR`: “선택하지 않은 항목이 있어요.” / `UNAUTHORIZED`: “온보딩 시간이 만료됐어요. 다시 시작해주세요.” |
| 사용 데이터 | `diagnosisId`, `onboardingToken`, `mydataConnectionId`, `privacySettingsId`, `accessToken` |

## 3. HOME-01 홈 대시보드

| 항목 | 명세 |
| --- | --- |
| 목적 | 내 금융 상태, 목표, 오늘 미션 후보, 또래 비교 teaser 표시 |
| 호출 API | `GET /api/home` |
| 로딩 | “오늘의 금융 상태를 불러오는 중이에요.” |
| 빈 상태 | 세션 없음: ONB-02 이동 CTA |
| 성공 | `goal`, `todayBudget`, `spendingSummary`, `assetSummary`, `peerTeaser`, `todayMissionCandidate` 표시 |
| 버튼 이동 | 또래 사례 보기 → `/explore/portfolios/{peerTeaser.portfolioId}` |
| error copy | `UNAUTHORIZED`: “세션이 만료됐어요. 다시 시작해주세요.” |
| 사용 데이터 | `goal.goalType`, `goal.label`, `todayBudget`, `spendingSummary.monthlySpent`, `spendingSummary.fixedCostRatio`, `assetSummary.cashLikeAssets`, `assetSummary.emergencyFundMonths`, `peerTeaser.portfolioId`, `todayMissionCandidate.recommendationSource` |

## 4. EXP-03 또래 포트폴리오 상세

| 항목 | 명세 |
| --- | --- |
| 목적 | 합성 또래 사례 카드와 루틴 표시 |
| 호출 API | `GET /api/explore/portfolios/{id}` |
| 로딩 | “또래 사례를 불러오는 중이에요.” |
| 성공 | `displayName`, `financialSummary`, `routineCards`, `privacyBadges` 표시 |
| 버튼 이동 | 비교하기 → `/explore/compare/{portfolioId}` |
| error copy | `PORTFOLIO_NOT_AVAILABLE`: “해당 사례 카드는 더 이상 공개되지 않아요.” |
| modal | privacy badge 클릭 시 “이 카드는 가명 처리되고 금액은 구간으로 표시됩니다.” |
| 사용 데이터 | `portfolioId`, `displayName`, `status`, `visibility`, `dataMode`, `financialSummary`, `routineCards`, `privacyBadges` |

## 5. EXP-04 1:1 비교

| 항목 | 명세 |
| --- | --- |
| 목적 | 내 상태와 또래 사례의 가장 큰 격차를 보여줌 |
| 호출 API | `POST /api/comparisons` |
| 로딩 | “내 데이터와 또래 사례를 비교하는 중이에요.” |
| 성공 | `mainGap`, `gapItems`, `nextAction`, `similarityScore` 표시 |
| 버튼 이동 | 3개월 시뮬레이션 보기 → `/simulations/{comparisonId}` |
| error copy | `PORTFOLIO_NOT_AVAILABLE`: “해당 사례 카드는 더 이상 공개되지 않아요.” |
| error copy | `COHORT_TOO_SMALL`: “비교 기준이 부족해 일부 항목을 숨겼어요.” |
| 표시 기준 | `gapItems`는 항목별 리스트 표시용이고, 강조/정렬 수치는 `mainGap.normalizedGap`만 사용 |
| 사용 데이터 | `comparisonId`, `similarityScore`, `mainGap.type`, `mainGap.label`, `mainGap.normalizedGap`, `gapItems`, `nextAction.scenarioType` |

## 6. SIM-01 3개월 시뮬레이션

| 항목 | 명세 |
| --- | --- |
| 목적 | 행동 변화가 3개월 뒤 지표에 미치는 영향을 보여줌 |
| 호출 API | `POST /api/simulations` |
| request 고정값 | `periodMonths = 3`, `monthlyAdditionalSaving = 100000` |
| 로딩 | “3개월 뒤 변화를 계산하는 중이에요.” |
| 성공 | `before`, `after`, `insight`, `disclaimer`, `nextAction` 표시 |
| 버튼 이동 | 오늘의 미션 만들기 → `/missions/new/{simulationId}` |
| error copy | `VALIDATION_ERROR`: “시뮬레이션 조건을 다시 확인해주세요.” |
| 사용 데이터 | `simulationId`, `before`, `after`, `insight`, `nextAction.missionTemplateId` |

## 7. MIS-01 오늘의 미션

| 항목 | 명세 |
| --- | --- |
| 목적 | 시뮬레이션 결과를 오늘 실행 가능한 미션으로 저장 |
| 호출 API | `POST /api/missions` |
| request 고정값 | `triggerSource = SIMULATION`, `recommendationSource = RULE_BASED` |
| 로딩 | “오늘 할 수 있는 미션으로 바꾸는 중이에요.” |
| 성공 | title, description, difficulty, verificationType, rewardPoints, privacySharePreview 표시 |
| 버튼 이동 | 홈으로 이동 → `/home`, 개인정보 설정 → `/settings/privacy` |
| disabled 조건 | 미션 생성 loading 중, `simulationId` 없음 |
| toast | “오늘의 미션이 만들어졌어요.” |
| 사용 데이터 | `missionId`, `title`, `description`, `difficulty`, `verificationType`, `status`, `privacySharePreview.containsAmount` |

## 8. SET-01 개인정보 설정

| 항목 | 명세 |
| --- | --- |
| 목적 | 공개 미리보기, 노출 필드 수정, 동의 철회 제공 |
| 호출 API | `GET /api/privacy/settings`, `PATCH /api/privacy/settings`, `POST /api/privacy/withdraw` |
| 로딩 | “공개 범위를 불러오는 중이에요.” |
| 성공 | `ownPortfolioId`, `preview`, `exposedFields`, `consentVersion`, `updatedAt` 표시 |
| 수정 modal | “선택한 항목만 익명 사례 카드에 노출됩니다.” |
| 철회 confirm | “익명 포트폴리오 공개를 철회할까요? 철회 후 이 사례 카드는 더 이상 조회되지 않습니다.” |
| toast | “공개 범위가 저장됐어요.”, “공개 동의가 철회됐어요.” |
| error copy | `FORBIDDEN`: “본인의 공개 범위만 변경할 수 있어요.” |
| 사용 데이터 | `ownPortfolioId`, `anonymousPortfolioOptIn`, `friendShareDefault`, `exposedFields`, `preview`, `consentVersion` |
