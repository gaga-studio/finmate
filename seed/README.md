# FinMate v1.0 Seed Data

## 목적

이 폴더는 P0 데모를 고정값으로 재현하기 위한 mock seed 기준본이다. 실제 마이데이터나 실제 금융 거래 데이터가 아니며, 발표/로컬 개발용 합성 데이터다.

## 고정 ID

| ID | 의미 |
| --- | --- |
| `demo-user-001` | 데모 사용자 |
| `P001` | 추천 페르소나 |
| `peer-portfolio-023` | P0 happy path 또래 포트폴리오 |
| `own-portfolio-001` | privacy path 공개 미리보기/철회 대상 |
| `diag-001` | 30초 진단 결과 |
| `onb-token-001` | 온보딩 토큰 예시 |
| `mydata-mock-001` | mock 마이데이터 동의 |
| `privacy-001` | 개인정보 설정 |
| `demo-token` | accessToken 예시 |
| `cmp-001` | 비교 결과 |
| `sim-001` | 시뮬레이션 결과 |
| `mis-001` | 오늘의 미션 |

## 파일

| 파일 | 설명 |
| --- | --- |
| `users.json` | 데모 사용자와 peer synthetic owner |
| `onboarding-diagnoses.json` | 30초 진단 결과 |
| `onboarding-sessions.json` | onboardingToken mapping |
| `mydata-connections.json` | mock 마이데이터 동의 |
| `privacy-settings.json` | 공개 설정과 preview 대상 |
| `personas.json` | 추천 페르소나 |
| `feature-vectors.json` | 비교/시뮬레이션 계산용 feature |
| `portfolios.json` | 익명 포트폴리오 snapshot |
| `mission-templates.json` | rule-based mission template |

## 데모 상태

- 초기 상태에서 `own-portfolio-001`은 `PUBLIC/ACTIVE`다.
- privacy withdraw 이후 `own-portfolio-001`은 `WITHDRAWN`, `withdrawnAt != null`이 된다.
- `peer-portfolio-023`은 철회 데모 대상이 아니며 항상 `PUBLIC/ACTIVE`를 유지한다.

