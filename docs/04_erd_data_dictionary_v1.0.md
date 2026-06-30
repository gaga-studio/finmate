# FinMate ERD / 데이터 딕셔너리 v1.0

## 1. v1.0 ERD 패치 요약

v1.0는 온보딩 실행 흐름을 구현 가능하게 만들기 위해 v1.0 ERD에 다음을 추가한다.

- `OnboardingDiagnosis` 테이블 추가
- `OnboardingSession.userId` 추가
- `OnboardingSession.status = IN_PROGRESS | COMPLETED | EXPIRED` 추가
- `MockMyDataConnection` 테이블 추가
- `onboardingToken`으로 `ConsentHistory.actorUserId`, `AuditLog.actorUserId`를 해석하는 관계 명시

## 2. 관계 요약

```text
User 1 - N OnboardingDiagnosis
User 1 - N OnboardingSession
User 1 - N MockMyDataConnection
OnboardingDiagnosis 1 - 1 OnboardingSession
OnboardingDiagnosis 1 - 0/1 MockMyDataConnection
User 1 - N FeatureVector
User 1 - N AnonymousPortfolio
User 1 - 1 PrivacySettings
User 1 - N ConsentHistory
User 1 - N AuditLog
AnonymousPortfolio 1 - N Comparison
Comparison 1 - N Simulation
Simulation 1 - N Mission
MissionTemplate 1 - N Mission
```

## 3. Enum

| Enum | Values |
| --- | --- |
| `goalType` | `EMERGENCY_FUND`, `REDUCE_SPENDING`, `START_INVESTING`, `PAY_DEBT` |
| `dataMode` | `MOCK_MYDATA`, `SYNTHETIC_PERSONA`, `REAL_MYDATA` |
| `friendShareDefault` | `NONE`, `MISSION_ONLY`, `ACHIEVEMENT_ONLY` |
| `onboardingStatus` | `IN_PROGRESS`, `COMPLETED`, `EXPIRED` |
| `connectionStatus` | `CONNECTED`, `REVOKED`, `EXPIRED` |
| `portfolioStatus` | `ACTIVE`, `WITHDRAWN`, `PRIVATE` |
| `scenarioType` | `FOLLOW_PEER_ROUTINE` |
| `missionStatus` | `CREATED`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED` |
| `missionDifficulty` | `EASY`, `NORMAL`, `HARD` |
| `triggerSource` | `SIMULATION`, `HOME`, `MANUAL` |
| `recommendationSource` | `RULE_BASED`, `LLM_OPTIONAL` |
| `verificationType` | `SELF_CHECK`, `DATA_CHECK`, `QUIZ` |
| `consentStatus` | `AGREED`, `WITHDRAWN` |
| `withdrawScope` | `ANONYMOUS_PORTFOLIO`, `DATA_CONTRIBUTION` |

## 4. ExposedFields allowed list

`PrivacySettings.exposedFields`는 다음 값만 허용한다.

| Field | 설명 |
| --- | --- |
| `ageBand` | 연령대 |
| `incomeBand` | 소득 구간 |
| `occupationStatus` | 직업/상태 |
| `householdType` | 가구 형태 |
| `goalType` | 금융 목표 |
| `financialSummary` | 구간화된 금융 요약 |
| `routineCards` | 행동 루틴 카드 |

노출 금지: 이름, 계좌번호, 카드번호, 주소, 거래처, 정확한 거래 시각, 식별 가능한 조합.

## 5. 테이블 정의

### User

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `demo-user-001` |
| `displayName` | string | no | 앱 내부 표시명 |
| `dataMode` | enum | no | P0는 `MOCK_MYDATA` |
| `createdAt` | datetime | no | 생성 시각 |
| `updatedAt` | datetime | no | 수정 시각 |

Indexes: `pk_user(id)`

### OnboardingDiagnosis

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `diag-001` |
| `userId` | string FK | no | 내부 연결 사용자 |
| `occupationStatus` | string | no | 진단 답변 |
| `incomeBand` | string | no | 진단 답변 |
| `householdType` | string | no | 진단 답변 |
| `goalType` | enum | no | 진단 목표 |
| `painPoint` | string | no | 핵심 어려움 |
| `recommendedPersonaId` | string FK | no | `P001` |
| `cohortLabel` | string | no | 화면 표시 코호트 |
| `createdAt` | datetime | no | 생성 시각 |

Indexes:

- `pk_onboarding_diagnosis(id)`
- `idx_onboarding_diagnosis_user(userId)`
- `idx_onboarding_diagnosis_goal(goalType)`

Relationship: `User 1 - N OnboardingDiagnosis`

### OnboardingSession

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `onb-session-001` |
| `userId` | string FK | no | `demo-user-001` |
| `diagnosisId` | string FK | no | `diag-001` |
| `onboardingTokenHash` | string | no | `onb-token-001` 해시 저장 |
| `status` | enum | no | `IN_PROGRESS`, `COMPLETED`, `EXPIRED` |
| `expiresAt` | datetime | no | 온보딩 토큰 만료 |
| `completedAt` | datetime | yes | 세션 전환 완료 시각 |
| `createdAt` | datetime | no | 생성 시각 |

Indexes:

- `pk_onboarding_session(id)`
- `idx_onboarding_session_user(userId)`
- `idx_onboarding_session_diagnosis(diagnosisId)`
- `idx_onboarding_session_token(onboardingTokenHash)`

Relationship:

- `User 1 - N OnboardingSession`
- `OnboardingDiagnosis 1 - 1 OnboardingSession`

### MockMyDataConnection

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `mydata-mock-001` |
| `userId` | string FK | no | `demo-user-001` |
| `diagnosisId` | string FK | no | `diag-001` |
| `consentVersion` | string | no | `mydata-mock-v1.0` |
| `scopesJson` | json | no | 동의 scope 배열 |
| `status` | enum | no | `CONNECTED` |
| `dataMode` | enum | no | `MOCK_MYDATA` |
| `agreedAt` | datetime | no | 동의 시각 |
| `revokedAt` | datetime | yes | 철회 시각 |

Indexes:

- `pk_mock_mydata_connection(id)`
- `idx_mock_mydata_connection_user(userId)`
- `idx_mock_mydata_connection_diagnosis(diagnosisId)`

Relationship:

- `User 1 - N MockMyDataConnection`
- `OnboardingDiagnosis 1 - 0/1 MockMyDataConnection`

### DemoSession

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `demo-session-001` |
| `userId` | string FK | no | `demo-user-001` |
| `selectedPersonaId` | string FK | no | `P001` |
| `goalType` | enum | no | `EMERGENCY_FUND` |
| `accessTokenHash` | string | no | `demo-token` 해시 저장 |
| `expiresAt` | datetime | no | 데모 만료 |
| `createdAt` | datetime | no | 생성 시각 |

Indexes: `idx_demo_session_user(userId)`, `idx_demo_session_token(accessTokenHash)`

### PersonaProfile

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `P001` |
| `label` | string | no | `비상금 루틴형 B` |
| `occupationStatus` | string | no | 코호트 기준 |
| `incomeBand` | string | no | 코호트 기준 |
| `householdType` | string | no | 코호트 기준 |
| `goalType` | enum | no | 목표 |
| `description` | string | no | 내부 설명 |

### FeatureVector

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | feature id |
| `userId` | string FK | no | 사용자 |
| `periodMonth` | string | no | `YYYY-MM` |
| `emergencyFundMonths` | decimal | no | 비상금 준비율 |
| `savingsRate` | decimal | no | 저축률 |
| `fixedCostRatio` | decimal | no | 고정비 비중 |
| `cashLikeAssets` | integer | no | 현금성 자산 |
| `monthlyEssentialSpending` | integer | no | 월평균 필수지출 |
| `createdAt` | datetime | no | 생성 시각 |

Indexes: `idx_feature_vector_user_period(userId, periodMonth)`

Relationship: `User 1 - N FeatureVector`

### AnonymousPortfolio

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `peer-portfolio-023`, `own-portfolio-001` |
| `ownerUserId` | string FK | no | 소유 사용자 |
| `personaProfileId` | string FK | yes | 또래 페르소나 |
| `displayName` | string | no | 가명 표시명 |
| `status` | enum | no | `ACTIVE`, `WITHDRAWN`, `PRIVATE` |
| `visibility` | string | no | `PUBLIC`, `PRIVATE` |
| `dataMode` | enum | no | `MOCK_MYDATA`, `SYNTHETIC_PERSONA` |
| `financialSummaryJson` | json | no | 빠른 데모용 snapshot |
| `featureSnapshotJson` | json | no | 비교 계산용 snapshot |
| `routineCardsJson` | json | no | 루틴 카드 snapshot |
| `createdAt` | datetime | no | 생성 시각 |
| `updatedAt` | datetime | no | 수정 시각 |
| `publishedAt` | datetime | yes | 공개 시각 |
| `withdrawnAt` | datetime | yes | 철회 시각 |

Indexes:

- `idx_anonymous_portfolio_owner(ownerUserId)`
- `idx_anonymous_portfolio_status(status)`

P0 데모는 peer feature를 별도 정규화하지 않고 `AnonymousPortfolio` snapshot JSON을 사용한다.

### Comparison

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `cmp-001` |
| `viewerUserId` | string FK | no | 비교 요청자 |
| `peerPortfolioId` | string FK | no | `peer-portfolio-023` |
| `mainGapType` | string | no | `EMERGENCY_FUND` |
| `similarityScore` | decimal | no | `0.84` |
| `gapItemsJson` | json | no | gap item 배열 |
| `createdAt` | datetime | no | 생성 시각 |

Indexes: `idx_comparison_viewer_created(viewerUserId, createdAt)`

### Simulation

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `sim-001` |
| `comparisonId` | string FK | no | `cmp-001` |
| `scenarioType` | enum | no | `FOLLOW_PEER_ROUTINE` |
| `periodMonths` | integer | no | `3` |
| `monthlyAdditionalSaving` | integer | no | `100000` |
| `beforeJson` | json | no | before metrics |
| `afterJson` | json | no | after metrics |
| `insight` | string | no | 화면 표시 문장 |
| `createdAt` | datetime | no | 생성 시각 |

Indexes: `idx_simulation_comparison(comparisonId)`

### MissionTemplate

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `MISSION_AUTO_TRANSFER_SMALL` |
| `goalType` | enum | no | `EMERGENCY_FUND` |
| `titleTemplate` | string | no | 미션 제목 템플릿 |
| `descriptionTemplate` | string | no | 미션 설명 템플릿 |
| `defaultDifficulty` | enum | no | `EASY` |
| `defaultVerificationType` | enum | no | `SELF_CHECK` |
| `defaultRewardPoints` | integer | no | `100` |
| `active` | boolean | no | 사용 여부 |

### Mission

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `mis-001` |
| `userId` | string FK | no | `demo-user-001` |
| `simulationId` | string FK | no | `sim-001` |
| `missionTemplateId` | string FK | no | `MISSION_AUTO_TRANSFER_SMALL` |
| `titleSnapshot` | string | no | `이번 달 비상금 자동이체 10만 원 설정하기` |
| `descriptionSnapshot` | string | no | 설명 snapshot |
| `difficulty` | enum | no | `EASY` |
| `triggerSource` | enum | no | `SIMULATION` |
| `recommendationSource` | enum | no | `RULE_BASED` |
| `verificationType` | enum | no | `SELF_CHECK` |
| `rewardPoints` | integer | no | 표시값 |
| `status` | enum | no | `CREATED` |
| `localDate` | date | no | 중복 생성 방지 기준 |
| `completedAt` | datetime | yes | 완료 시각 |
| `createdAt` | datetime | no | 생성 시각 |

Unique:

- `uniq_mission_daily_template(userId, simulationId, missionTemplateId, localDate)`

포인트는 P0에서 `rewardPoints` 표시값만 둔다. 적립 원장 저장은 P1의 `PointWallet`, `PointTransaction` 범위다.

### PrivacySettings

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | `privacy-001` |
| `userId` | string FK | no | `demo-user-001` |
| `anonymousPortfolioOptIn` | boolean | no | 익명 포트폴리오 공개 동의 |
| `friendShareDefault` | enum | no | `NONE`, `MISSION_ONLY`, `ACHIEVEMENT_ONLY` |
| `ownPortfolioId` | string FK | no | 공개 미리보기/철회 대상 포트폴리오 |
| `exposedFields` | json | no | allowed list 배열 |
| `consentVersion` | string | no | `privacy-v1.0` |
| `createdAt` | datetime | no | 생성 시각 |
| `updatedAt` | datetime | no | 수정 시각 |

Indexes: `uniq_privacy_settings_user(userId)`

### ConsentHistory

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | consent id |
| `userId` | string FK | no | 동의 주체 |
| `consentItem` | string | no | `PRIVACY_SETTINGS`, `ANONYMOUS_PORTFOLIO`, `MYDATA_MOCK` |
| `consentVersion` | string | no | 버전 |
| `status` | enum | no | `AGREED`, `WITHDRAWN` |
| `agreedAt` | datetime | yes | 동의 시각 |
| `withdrawnAt` | datetime | yes | 철회 시각 |
| `source` | string | no | `ONBOARDING`, `SETTINGS` |
| `actorUserId` | string FK | no | accessToken 전에는 onboardingToken에서 해석 |
| `requestId` | string | no | 요청 추적 |

Indexes: `idx_consent_history_user_item(userId, consentItem)`

### AuditLog

| Column | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | string PK | no | audit id |
| `actorUserId` | string FK | no | accessToken 전에는 onboardingToken에서 해석 |
| `eventType` | string | no | audit event |
| `targetType` | string | yes | 대상 타입 |
| `targetId` | string | yes | 대상 id |
| `requestId` | string | no | 요청 추적 |
| `payloadJson` | json | yes | 민감정보 제외한 추가 정보 |
| `createdAt` | datetime | no | 생성 시각 |

Indexes:

- `idx_audit_log_actor_created(actorUserId, createdAt)`
- `idx_audit_log_event_created(eventType, createdAt)`
