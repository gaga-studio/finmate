# FinMate 개발 태스크 백로그 v1.0

## 사용 원칙

- 모든 태스크의 상태 기본값은 `TODO`.
- owner는 실제 담당자 배정 전까지 `[담당자 TBD]`로 둔다.
- P0 공개 API 추가가 필요한 태스크는 즉시 로드맵 결정 회의로 올린다.
- Birthday Wish Money, Social, Points, Quiz는 이 백로그에서 P0 구현 대상이 아니다.

## Backend

| ID | Task | Owner | Related API/Screen/Table | Acceptance Criteria | Status |
| --- | --- | --- | --- | --- | --- |
| BE-01 | OpenAPI v1.0 기반 route skeleton 생성 | `[담당자 TBD]` | P0 API 12개 | 12개 path와 method가 모두 라우팅되고 `/api/coach/recommendations`가 없음 | TODO |
| BE-02 | onboardingToken 발급/해석 구현 | `[담당자 TBD]` | `POST /api/onboarding/diagnosis`, `OnboardingSession` | `diag-001` 생성 후 `onb-token-001 -> demo-user-001` 해석 가능 | TODO |
| BE-03 | mock mydata consent 저장 구현 | `[담당자 TBD]` | `POST /api/mydata/mock-consent`, `MockMyDataConnection` | `mydata-mock-001` 저장, `MYDATA_CONSENT_GRANTED` audit 기록 | TODO |
| BE-04 | privacy consent 저장 구현 | `[담당자 TBD]` | `POST /api/privacy/consents`, `PrivacySettings`, `ConsentHistory` | `privacy-001` 생성, actor는 onboardingToken에서 해석 | TODO |
| BE-05 | demo session 전환 구현 | `[담당자 TBD]` | `POST /api/demo/session`, `DemoSession` | `accessToken=demo-token`, `OnboardingSession.status=COMPLETED` | TODO |
| BE-06 | home mock response 구현 | `[담당자 TBD]` | `GET /api/home`, `HOME-01` | `goal`, `peerTeaser.portfolioId=peer-portfolio-023` 포함 | TODO |
| BE-07 | portfolio 조회/철회 상태 처리 구현 | `[담당자 TBD]` | `GET /api/explore/portfolios/{id}`, `AnonymousPortfolio` | own 철회 후 410, peer는 200 유지 | TODO |
| BE-08 | comparison 계산 구현 | `[담당자 TBD]` | `POST /api/comparisons`, `Comparison` | `mainGap.type=EMERGENCY_FUND`, `similarityScore=0.84` | TODO |
| BE-09 | simulation 계산 구현 | `[담당자 TBD]` | `POST /api/simulations`, `Simulation` | 월 10만 원, 3개월, `after.emergencyFundMonths=0.7` | TODO |
| BE-10 | mission 생성 idempotency 구현 | `[담당자 TBD]` | `POST /api/missions`, `Mission` | 같은 `simulationId+missionTemplateId+localDate`면 `mis-001` 재사용 | TODO |
| BE-11 | privacy partial update 구현 | `[담당자 TBD]` | `PATCH /api/privacy/settings` | omitted field 유지, 빈 body 422, allowed list 검증 | TODO |
| BE-12 | privacy withdraw 구현 | `[담당자 TBD]` | `POST /api/privacy/withdraw` | `ANONYMOUS_PORTFOLIO`는 `portfolioId` 필수, 이미 철회돼도 200 | TODO |

## Frontend

| ID | Task | Owner | Related API/Screen/Table | Acceptance Criteria | Status |
| --- | --- | --- | --- | --- | --- |
| FE-01 | ONB-02 진단/동의 단계 연결 | `[담당자 TBD]` | ONB-02, diagnosis/mock-consent/privacy-consents/demo-session | `onboardingToken`으로 4단계가 끊기지 않음 | TODO |
| FE-02 | HOME-01 peer teaser CTA 연결 | `[담당자 TBD]` | HOME-01, `GET /api/home` | `peerTeaser.portfolioId`로 EXP-03 진입 | TODO |
| FE-03 | EXP-03 portfolio 상태 처리 | `[담당자 TBD]` | EXP-03, portfolio API | `ACTIVE`, `WITHDRAWN/410`, `404` 문구 분기 | TODO |
| FE-04 | EXP-04 비교 결과 표시 | `[담당자 TBD]` | EXP-04, comparisons | `gapItems`와 `mainGap` 표시 | TODO |
| FE-05 | SIM-01 시뮬레이션 표시 | `[담당자 TBD]` | SIM-01, simulations | `insight`, before/after 값 표시 | TODO |
| FE-06 | MIS-01 미션 카드 표시 | `[담당자 TBD]` | MIS-01, missions | 제목이 `이번 달 비상금 자동이체 10만 원 설정하기`로 표시 | TODO |
| FE-07 | SET-01 공개 미리보기/철회 | `[담당자 TBD]` | SET-01, privacy APIs | 공개 미리보기, partial update, withdraw 완료 문구 표시 | TODO |

## Data/Seed

| ID | Task | Owner | Related API/Screen/Table | Acceptance Criteria | Status |
| --- | --- | --- | --- | --- | --- |
| DATA-01 | seed JSON 로더 작성 | `[담당자 TBD]` | `seed` | 9개 JSON 파일을 로컬 DB 또는 mock store에 적재 | TODO |
| DATA-02 | fixed ID 매핑 보호 테스트 | `[담당자 TBD]` | seed 전체 | `demo-user-001`, `peer-portfolio-023`, `own-portfolio-001` 누락 시 실패 | TODO |
| DATA-03 | demo reset 절차 자동화 후보 검토 | `[담당자 TBD]` | `scripts/demo-reset.md` | public API 추가 없이 local script/runbook으로 복구 가능 | TODO |

## QA/Demo

| ID | Task | Owner | Related API/Screen/Table | Acceptance Criteria | Status |
| --- | --- | --- | --- | --- | --- |
| QA-01 | HTTP request template 수동 리허설 | `[담당자 TBD]` | `requests/finmate-p0-v1.0.http` | 12단계 + privacy verification 2개가 순서대로 설명됨 | TODO |
| QA-02 | contract validator CI 후보 등록 | `[담당자 TBD]` | `scripts/validate_contract.py` | 로컬에서 `FinMate P0 contract validation passed` 출력 | TODO |
| QA-03 | 데모 happy path 리허설 | `[담당자 TBD]` | P0 전체 | 홈부터 미션까지 7분 발표 흐름 안에서 완주 | TODO |
| QA-04 | privacy path 리허설 | `[담당자 TBD]` | SET-01, portfolio API | own 철회 후 410, peer 200을 화면에서 확인 | TODO |

## Presentation support

| ID | Task | Owner | Related API/Screen/Table | Acceptance Criteria | Status |
| --- | --- | --- | --- | --- | --- |
| PRES-01 | 7분 발표 데모 순서와 API 순서 대조 | `[담당자 TBD]` | v1.0 발표 문서, v1.0 requests | 발표자가 누르는 순서와 API 흐름이 일치 | TODO |
| PRES-02 | 문제 근거 placeholder 관리 | `[담당자 TBD]` | v1.0 발표 기획서 | 인터뷰/설문/시장 수치가 실제 자료 확보 전까지 비어 있음 | TODO |
| PRES-03 | Social Impact 표현 QA | `[담당자 TBD]` | Birthday Wish Money 슬라이드 | P2 확장으로만 설명, 투자상품 가입 권유 표현 없음 | TODO |

