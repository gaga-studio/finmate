# FinMate 개발 핸드오프/API 명세서 v1.0

## 1. 패치 목적

v1.0는 v1.0의 제품 범위와 7분 발표 구조를 유지하면서 개발 착수 직전 계약 빈틈을 메우는 실행 패치다. 핵심은 API를 늘리는 것이 아니라 기존 P0 12개 API가 구현 중 흔들리지 않도록 인증, actor 해석, privacy update, withdraw, seed mapping을 고정하는 것이다.

## 2. 고정 원칙

| 원칙 | 내용 |
| --- | --- |
| P0 API 수 | 공개 P0 API는 12개로 유지 |
| 신규 API | 로드맵 결정 없이 신규 P0 API 추가 금지 |
| AI Coach | rule-based 기본, LLM optional provider |
| 공개 제외 | `POST /api/coach/recommendations`는 P0 공개 API가 아님 |
| Birthday Wish Money | P2 Social Impact 확장, P0 편입 금지 |
| 발표 근거 | 인터뷰/설문/시장 수치는 실제 확보 전까지 placeholder 유지 |

## 3. P0 API 12개

| 순서 | Method | Path | 목적 | 인증 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/api/onboarding/diagnosis` | 30초 진단 생성 | none |
| 2 | POST | `/api/mydata/mock-consent` | mock 마이데이터 동의 | `onboardingToken` |
| 3 | POST | `/api/privacy/consents` | 개인정보/공개 동의 | `onboardingToken` |
| 4 | POST | `/api/demo/session` | 데모 세션 시작 | `onboardingToken` |
| 5 | GET | `/api/home` | 홈 요약 | `accessToken` |
| 6 | GET | `/api/explore/portfolios/{id}` | 또래/내 공개 포트폴리오 조회 | `accessToken` |
| 7 | POST | `/api/comparisons` | 1:1 비교 생성 | `accessToken` |
| 8 | POST | `/api/simulations` | 3개월 시뮬레이션 생성 | `accessToken` |
| 9 | POST | `/api/missions` | 오늘의 미션 생성 | `accessToken` |
| 10 | GET | `/api/privacy/settings` | 공개 설정/미리보기 조회 | `accessToken` |
| 11 | PATCH | `/api/privacy/settings` | 공개 설정 partial update | `accessToken` |
| 12 | POST | `/api/privacy/withdraw` | 익명 포트폴리오 공개 철회 | `accessToken` |

## 4. 인증과 actor 해석

### 4.1 온보딩 중 userId 금지

프론트는 온보딩 중 `userId`를 request body/query로 보내지 않는다. `POST /api/onboarding/diagnosis`는 서버 내부에서 데모 사용자인 `demo-user-001`을 생성하거나 연결하고, 응답으로 `onboardingToken`을 반환한다.

### 4.2 onboardingToken mapping

`onboardingToken`은 내부적으로 다음 정보를 해석한다.

| 필드 | 고정값/설명 |
| --- | --- |
| `token` | `onb-token-001` |
| `actorUserId` | `demo-user-001` |
| `diagnosisId` | `diag-001` |
| `status` | `IN_PROGRESS` |
| `expiresAt` | 데모 세션 만료 시각 |

`accessToken` 발급 전 생성되는 `ConsentHistory.actorUserId`, `AuditLog.actorUserId`는 `onboardingToken`에서 해석한다.

### 4.3 accessToken 이후 userId 신뢰 금지

홈 진입 이후 API는 `Authorization: Bearer demo-token`에서 `viewerUserId`를 추출한다. 제품 단계에서는 body/query/path의 `userId`를 권한 판단에 사용하지 않는다.

## 5. 공통 status/error 규칙

| 상황 | HTTP | code |
| --- | --- | --- |
| 성공 | `200` | - |
| 인증 없음/만료 | `401` | `UNAUTHORIZED` |
| 권한 없음 | `403` | `FORBIDDEN` |
| 리소스 없음 | `404` | `NOT_FOUND` |
| 철회/비공개 포트폴리오 | `410` | `PORTFOLIO_NOT_AVAILABLE` |
| 검증 오류 | `422` | `VALIDATION_ERROR` |

Error response:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "fieldErrors": [
    {
      "field": "portfolioId",
      "message": "portfolioId is required when scope is ANONYMOUS_PORTFOLIO."
    }
  ]
}
```

## 6. API 상세 패치

### 6.1 POST /api/onboarding/diagnosis

목적: 30초 진단을 저장하고 온보딩 actor를 만들거나 연결한다.

Request:

```json
{
  "occupationStatus": "PART_TIME_STUDENT",
  "incomeBand": "INCOME_150_250",
  "householdType": "SINGLE",
  "goalType": "EMERGENCY_FUND",
  "painPoint": "SAVE_CONSISTENTLY"
}
```

Response:

```json
{
  "diagnosisId": "diag-001",
  "onboardingToken": "onb-token-001",
  "goalType": "EMERGENCY_FUND",
  "recommendedPersonaId": "P001",
  "cohortLabel": "학생/알바 · 월소득 150~250만 원 · 1인 가구",
  "expiresAt": "2026-06-30T15:30:00+09:00"
}
```

Implementation note:

- 서버는 내부적으로 `demo-user-001`을 생성하거나 기존 데모 사용자를 연결한다.
- `OnboardingDiagnosis.userId = demo-user-001`로 저장한다.
- `OnboardingSession.userId = demo-user-001`, `status = IN_PROGRESS`로 저장한다.
- 프론트는 `userId`를 보내지 않는다.

### 6.2 POST /api/mydata/mock-consent

목적: 발표 데모용 합성 마이데이터 연결 동의를 기록한다.

Auth: `Authorization: Bearer onb-token-001`

Request:

```json
{
  "diagnosisId": "diag-001",
  "consentVersion": "mydata-mock-v1.0",
  "scopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
}
```

Response:

```json
{
  "mydataConnectionId": "mydata-mock-001",
  "status": "CONNECTED",
  "dataMode": "MOCK_MYDATA",
  "agreedAt": "2026-06-30T15:01:00+09:00"
}
```

Audit: `MYDATA_CONSENT_GRANTED`, actor는 `onboardingToken`에서 해석한다.

### 6.3 POST /api/privacy/consents

목적: 개인정보 제공, 익명 포트폴리오 공개, 친구 공유 기본값 동의를 기록한다.

Auth: `Authorization: Bearer onb-token-001`

Request:

```json
{
  "anonymousPortfolioOptIn": true,
  "friendShareDefault": "NONE",
  "exposedFields": ["ageBand", "incomeBand", "goalType", "financialSummary", "routineCards"],
  "consentVersion": "privacy-v1.0"
}
```

Response:

```json
{
  "privacySettingsId": "privacy-001",
  "consentHistoryIds": ["consent-privacy-001", "consent-portfolio-001"],
  "previewAvailable": true
}
```

Audit: `CONSENT_UPDATED`, actor는 `onboardingToken`에서 해석한다.

### 6.4 POST /api/demo/session

목적: 온보딩 결과를 `accessToken` 기반 데모 세션으로 전환한다.

Auth: `Authorization: Bearer onb-token-001`

Request:

```json
{
  "mode": "QUICK_DIAGNOSIS",
  "diagnosisId": "diag-001",
  "mydataConnectionId": "mydata-mock-001"
}
```

Response:

```json
{
  "userId": "demo-user-001",
  "accessToken": "demo-token",
  "selectedPersonaId": "P001",
  "goalType": "EMERGENCY_FUND",
  "expiresAt": "2026-06-30T16:00:00+09:00"
}
```

Implementation note:

- `OnboardingSession.status = COMPLETED`로 변경한다.
- 이후 모든 P0 API는 `accessToken`을 사용한다.

### 6.5 GET /api/home

목적: 홈 금융 요약과 Explore 진입 카드 제공.

Response 주요 필드:

```json
{
  "userId": "demo-user-001",
  "goal": {
    "goalType": "EMERGENCY_FUND",
    "label": "비상금 1개월 만들기"
  },
  "todayBudget": 18000,
  "spendingSummary": {
    "monthlySpent": 420000,
    "fixedCostRatio": 0.42
  },
  "assetSummary": {
    "cashLikeAssets": 400000,
    "emergencyFundMonths": 0.4
  },
  "todayMissionCandidate": {
    "title": "이번 달 비상금 자동이체 10만 원 설정하기",
    "recommendationSource": "RULE_BASED"
  },
  "peerTeaser": {
    "portfolioId": "peer-portfolio-023",
    "title": "비상금 루틴형 B",
    "similarityScore": 0.84,
    "mainDifference": "비상금 준비율이 1.6개월 차이"
  }
}
```

### 6.6 GET /api/explore/portfolios/{id}

목적: 동의 기반 가명/마스킹 또래 사례 또는 내 공개 미리보기 조회.

P0 happy path:

- `peer-portfolio-023` 조회는 `200`.

Privacy path:

- `own-portfolio-001` 철회 후 조회는 `410 PORTFOLIO_NOT_AVAILABLE`.
- `peer-portfolio-023`은 철회 대상이 아니므로 계속 `200`.

Response 주요 필드:

```json
{
  "portfolioId": "peer-portfolio-023",
  "displayName": "비상금 루틴형 B",
  "status": "ACTIVE",
  "visibility": "PUBLIC",
  "dataMode": "SYNTHETIC_PERSONA",
  "financialSummary": {
    "emergencyFundMonths": 1.8,
    "savingsRate": 0.21,
    "fixedCostRatio": 0.38
  },
  "routineCards": [
    {
      "title": "월급 다음 날 10만 원 자동이체",
      "description": "소비 전 저축을 먼저 고정합니다."
    }
  ],
  "privacyBadges": ["가명 처리", "거래처 비공개", "금액 구간 표시"]
}
```

### 6.7 POST /api/comparisons

목적: 내 데이터와 또래 포트폴리오의 차이를 계산한다.

Request:

```json
{
  "peerPortfolioId": "peer-portfolio-023"
}
```

Response 핵심:

```json
{
  "comparisonId": "cmp-001",
  "mainGap": {
    "type": "EMERGENCY_FUND",
    "label": "비상금 준비율",
    "score": 0.5333
  },
  "similarityScore": 0.84,
  "gapItems": [
    {
      "type": "EMERGENCY_FUND",
      "userValue": 0.4,
      "peerValue": 1.8,
      "unit": "months"
    }
  ],
  "nextAction": {
    "label": "3개월 시뮬레이션 보기",
    "scenarioType": "FOLLOW_PEER_ROUTINE"
  }
}
```

mainGap 계산식:

```text
emergencyFundGap = abs(peerEmergencyFundMonths - userEmergencyFundMonths) / 3.0
savingsRateGap = abs(peerSavingsRate - userSavingsRate) / 0.3
fixedCostGap = abs(userFixedCostRatio - peerFixedCostRatio) / 0.5

mainGap = max(emergencyFundGap, savingsRateGap, fixedCostGap)
tie-break = EMERGENCY_FUND -> SAVINGS_RATE -> FIXED_COST_RATIO
```

similarityScore 계산식:

```text
incomeBandMatch * 0.35
+ occupationStatusMatch * 0.25
+ demoContextMatch * 0.20
+ goalTypeMatch * 0.20
```

데모 고정값은 `0.84`를 사용한다.

`demoContextMatch`는 raw 가구형태 일치 점수가 아니라 P0 발표용 정규화 맥락 점수다. `similarityScore=0.84`는 P0 발표용 `DEMO_NORMALIZED` 점수이며, 원본 `feature_matrix.csv`의 raw cluster distance나 실제 유사도 모델 결과가 아니라 P001/P003의 페르소나 방향성을 유지하면서 7분 데모 비교 흐름을 설명하기 쉽게 정규화한 값이다.

### 6.8 POST /api/simulations

목적: 3개월 행동 변화 예측.

Request:

```json
{
  "comparisonId": "cmp-001",
  "scenarioType": "FOLLOW_PEER_ROUTINE",
  "monthlyAdditionalSaving": 100000,
  "periodMonths": 3
}
```

Response:

```json
{
  "simulationId": "sim-001",
  "scenarioType": "FOLLOW_PEER_ROUTINE",
  "periodMonths": 3,
  "monthlyAdditionalSaving": 100000,
  "before": {
    "emergencyFundMonths": 0.4,
    "cashLikeAssets": 400000
  },
  "after": {
    "emergencyFundMonths": 0.7,
    "cashLikeAssets": 700000
  },
  "insight": "3개월 동안 매월 10만 원을 먼저 떼어두면 비상금 준비율이 0.4개월에서 0.7개월로 올라갑니다.",
  "nextAction": {
    "label": "오늘의 미션 만들기",
    "missionTemplateId": "MISSION_AUTO_TRANSFER_SMALL"
  },
  "disclaimer": "이 시뮬레이션은 합성 데이터 기반 가정이며 금융상품 권유나 수익 보장이 아닙니다."
}
```

계산식:

```text
3개월 후 예상 비상금 = 현재 비상금 + 월추가저축액 * 3
비상금 준비율 = 현금성 자산 / 월평균 필수지출
```

### 6.9 POST /api/missions

목적: 시뮬레이션 결과를 오늘의 행동으로 변환한다.

Request:

```json
{
  "simulationId": "sim-001",
  "missionTemplateId": "MISSION_AUTO_TRANSFER_SMALL",
  "triggerSource": "SIMULATION",
  "recommendationSource": "RULE_BASED",
  "difficulty": "EASY"
}
```

Response:

```json
{
  "missionId": "mis-001",
  "title": "이번 달 비상금 자동이체 10만 원 설정하기",
  "description": "월급이나 용돈이 들어온 다음 날 비상금 계좌로 10만 원 자동이체를 예약합니다.",
  "difficulty": "EASY",
  "verificationType": "SELF_CHECK",
  "rewardPoints": 100,
  "status": "CREATED",
  "localDate": "2026-06-30",
  "privacySharePreview": {
    "shareableText": "오늘 비상금 자동이체 미션을 시작했어요.",
    "containsAmount": false
  }
}
```

Idempotency:

- `simulationId + missionTemplateId + localDate`가 같으면 기존 `missionId`를 반환한다.
- 같은 `Idempotency-Key` 재요청은 동일 response를 반환한다.
- `MissionRequest`에는 `source` 필드를 사용하지 않는다.

### 6.10 GET /api/privacy/settings

목적: SET-01 공개 미리보기와 현재 공개 범위 조회.

Response:

```json
{
  "privacySettingsId": "privacy-001",
  "anonymousPortfolioOptIn": true,
  "friendShareDefault": "NONE",
  "ownPortfolioId": "own-portfolio-001",
  "exposedFields": ["ageBand", "incomeBand", "goalType", "financialSummary", "routineCards"],
  "preview": {
    "portfolioId": "own-portfolio-001",
    "displayName": "나의 공개 미리보기",
    "hiddenFields": ["name", "accountNumber", "merchantName", "exactTransactionTime"]
  },
  "consentVersion": "privacy-v1.0",
  "updatedAt": "2026-06-30T15:02:00+09:00"
}
```

Audit: `PRIVACY_PREVIEW_VIEWED`.

### 6.11 PATCH /api/privacy/settings

목적: 익명 포트폴리오 공개 여부, 친구 공유 기본값, 노출 필드를 부분 수정한다.

Partial update 규칙:

- request body에 없는 필드는 기존 값을 유지한다.
- 빈 body `{}`는 `422 VALIDATION_ERROR`.
- `exposedFields`는 allowed list에 있는 값만 허용한다.
- `anonymousPortfolioOptIn=false`는 즉시 철회가 아니라 공개 중지 설정이다. 이미 발행된 포트폴리오 철회는 `POST /api/privacy/withdraw`를 사용한다.

Request:

```json
{
  "friendShareDefault": "MISSION_ONLY",
  "exposedFields": ["ageBand", "goalType", "financialSummary"]
}
```

Response:

```json
{
  "privacySettingsId": "privacy-001",
  "anonymousPortfolioOptIn": true,
  "friendShareDefault": "MISSION_ONLY",
  "exposedFields": ["ageBand", "goalType", "financialSummary"],
  "consentVersion": "privacy-v1.0",
  "updatedAt": "2026-06-30T15:20:00+09:00"
}
```

Audit: `CONSENT_UPDATED`.

### 6.12 POST /api/privacy/withdraw

목적: 익명 포트폴리오 공개 또는 데이터 기여 동의를 철회한다.

P0에서 사용하는 scope:

```json
{
  "scope": "ANONYMOUS_PORTFOLIO",
  "portfolioId": "own-portfolio-001",
  "reason": "DEMO_PRIVACY_CHECK"
}
```

확장 scope:

```json
{
  "scope": "DATA_CONTRIBUTION",
  "reason": "USER_REQUEST"
}
```

규칙:

- `scope=ANONYMOUS_PORTFOLIO`이면 `portfolioId` 필수.
- `scope=DATA_CONTRIBUTION`이면 `portfolioId` optional.
- P0 데모는 `ANONYMOUS_PORTFOLIO`만 사용한다.
- 이미 철회된 상태에서도 `200 OK + status: WITHDRAWN`을 반환한다.
- 철회 후 `own-portfolio-001` 조회는 `410 PORTFOLIO_NOT_AVAILABLE`.
- 철회 후 `peer-portfolio-023` 조회는 계속 `200`.

Response:

```json
{
  "status": "WITHDRAWN",
  "withdrawnAt": "2026-06-30T15:25:00+09:00",
  "affectedPortfolioIds": ["own-portfolio-001"]
}
```

Audit: `CONSENT_WITHDRAWN`.

## 7. Audit event 목록

| Event | Trigger |
| --- | --- |
| `MYDATA_CONSENT_GRANTED` | mock 마이데이터 동의 |
| `CONSENT_UPDATED` | 개인정보 동의 생성/수정 |
| `PRIVACY_PREVIEW_VIEWED` | 공개 미리보기 조회 |
| `PORTFOLIO_VIEWED` | 포트폴리오 조회 |
| `COMPARISON_CREATED` | 비교 생성 |
| `SIMULATION_CREATED` | 시뮬레이션 생성 |
| `MISSION_CREATED` | 미션 생성 |
| `CONSENT_WITHDRAWN` | 공개/기여 철회 |

## 8. 구현 체크

- `GET /api/home` 응답의 `peerTeaser.portfolioId`로 Explore 진입 가능.
- `peer-portfolio-023`과 `own-portfolio-001`을 혼동하지 않는다.
- privacy withdraw는 `own-portfolio-001`만 대상으로 한다.
- Mission request/response는 `triggerSource`, `recommendationSource`를 쓴다.
- 화면 명세에서 쓰는 `goal`, `routineCards`, `gapItems`, `insight`, `exposedFields`가 API 응답에 있다.
- seed와 OpenAPI examples의 고정 ID가 일치한다.
