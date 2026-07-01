# FinMate v1.0 Demo Reset Runbook

## 목적

발표 리허설 또는 데모 실패 후 P0 mock 상태를 처음 상태로 되돌린다. 이 문서는 local/demo 전용이며 public P0 API가 아니다. `POST /api/demo/reset`을 공개 API로 추가하지 않는다.

## Reset 대상

| 대상 | Reset 값 |
| --- | --- |
| `own-portfolio-001.status` | `ACTIVE` |
| `own-portfolio-001.visibility` | `PUBLIC` |
| `own-portfolio-001.withdrawnAt` | `null` |
| `peer-portfolio-023.status` | `ACTIVE` 유지 |
| `peer-portfolio-023.visibility` | `PUBLIC` 유지 |
| `PrivacySettings.anonymousPortfolioOptIn` | `true` |
| `PrivacySettings.friendShareDefault` | `NONE` |
| `PrivacySettings.exposedFields` | `["ageBand", "incomeBand", "goalType", "financialSummary", "routineCards"]` |
| `Mission.mis-001.status` | 필요 시 삭제하거나 `CREATED`로 복구 |
| `OnboardingSession.status` | 새 리허설이면 `IN_PROGRESS`, 세션 유지면 `COMPLETED` |

## 수동 절차

1. seed 기준본을 확인한다.

```bash
ls fixtures/app-seed
```

2. 로컬 mock store 또는 DB를 seed 상태로 되돌린다.

```text
fixtures/app-seed/users.json
fixtures/app-seed/onboarding-diagnoses.json
fixtures/app-seed/onboarding-sessions.json
fixtures/app-seed/mydata-connections.json
fixtures/app-seed/privacy-settings.json
fixtures/app-seed/personas.json
fixtures/app-seed/feature-vectors.json
fixtures/app-seed/portfolios.json
fixtures/app-seed/mission-templates.json
```

3. privacy verification 상태를 확인한다.

```text
GET /api/explore/portfolios/own-portfolio-001 -> 200 before withdraw
POST /api/privacy/withdraw scope=ANONYMOUS_PORTFOLIO portfolioId=own-portfolio-001 -> 200
GET /api/explore/portfolios/own-portfolio-001 -> 410 after withdraw
GET /api/explore/portfolios/peer-portfolio-023 -> 200 always
```

4. 다음 발표 리허설 전에는 `own-portfolio-001`만 다시 공개 상태로 되돌린다.

## 주의

- peer 사례인 `peer-portfolio-023`은 철회 데모 대상이 아니다.
- privacy 철회 데모는 반드시 `own-portfolio-001`로만 수행한다.
- reset은 로컬 데모 준비물이지 제품 기능이 아니다.
- 실제 서비스에서는 철회 이력과 audit log를 되돌리지 않는다.
