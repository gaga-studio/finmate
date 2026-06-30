# FinMate v1.0 Seed Data

## 목적

이 폴더는 P0 데모를 고정값으로 재현하기 위한 mock seed 기준본이다. 실제 마이데이터나 실제 금융 거래 데이터가 아니며, 발표/로컬 개발용 합성 데이터다.

## 원본 합성 데이터셋과의 관계

FinMate seed는 `gaga-studio/financial-sns-mydata-202606`의 합성 청년 금융 데이터셋을 출처로 삼는다. 다만 이 폴더의 값은 원본 ledger 전체를 그대로 복사한 것이 아니라, P0 발표 흐름과 API 계약에 맞게 정규화한 mock 기준본이다.

| Seed 대상 | 원본 persona | 설명 |
| --- | --- | --- |
| `demo-user-001` | `P001` | 저축률이 낮은 1인가구 데모 사용자 기준 |
| `own-portfolio-001` | `P001` | 본인 공개 미리보기/철회 대상의 마스킹 snapshot |
| `peer-portfolio-023` | `P003` | 비상금 목표와 저축 루틴을 가진 또래 사례 |

원본 출처와 P0 subset은 `data/source-dataset.md`와 `data/p0/`에서 확인한다. 공개 레포에는 원본 `bundles/`, 전체 `ledger_all.csv`, Excel 파일을 포함하지 않는다.

## 고정 ID

| ID | 의미 |
| --- | --- |
| `demo-user-001` | 데모 사용자 |
| `P001` | 데모 사용자/본인 공개 미리보기 source persona |
| `P003` | 또래 포트폴리오 source persona |
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
