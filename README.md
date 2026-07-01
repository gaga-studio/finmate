# FinMate

FinMate는 로그인한 사용자가 금융 루틴, 미션, 기록, 포인트, 친구 피드, 생일펀드를 관리하는 모바일 금융 습관 앱입니다. 합성 금융 데이터와 AI 코치 연결은 확장 가능한 계약으로 열어두고, 앱 자체는 Postgres에 사용자별 상태를 저장합니다.

핵심 흐름:

```text
30초 진단
-> mock 마이데이터 동의
-> 개인정보 동의
-> 홈 금융 요약
-> 또래 사례
-> 1:1 비교
-> 3개월 시뮬레이션
-> 오늘의 미션
-> 개인정보 공개 미리보기/철회
```

## 왜 필요한가

청년에게 부족한 것은 금융 지식 자체보다 “오늘 움직이게 하는 계기”입니다. FinMate는 SNS식 비교 자극을 소비가 아니라 비상금, 저축 루틴, 작은 금융 행동으로 전환합니다.

현재 레포는 제품형 MVP 전환을 포함합니다. 이메일 로그인, JWT access token, HttpOnly refresh cookie, Postgres/Flyway 기반 상태 저장, 포인트/가상머니 원장, 생일펀드 참여, AI fallback 코치 계약이 들어 있습니다. 실제 금융기관 MyData, 실제 결제/송금, 실제 푸시는 포함하지 않습니다.

향후 공통 타입을 붙인다면 권장 구조는 `apps/web`(프론트), `apps/api`(백엔드/API), `packages/shared`(공통 DTO/schema)입니다.

## Quick Start

처음 실행은 Docker 기준을 권장합니다.

```bash
docker compose up --build
```

실행 후 브라우저에서 `http://localhost:5173/login`을 엽니다.

```text
/signup
-> /onboarding
-> /home
-> /missions/mission-food/feedback
-> /records/history
-> /birthday-funds/fund-jiwoo/contribute
-> /profile
```

API 상태는 `http://localhost:8080/health`에서 확인합니다.

## Dataset Source

FinMate v1.0 seed는 [`gaga-studio/financial-sns-mydata-202606`](https://github.com/gaga-studio/financial-sns-mydata-202606)의 합성 청년 금융 데이터셋을 출처로 삼되, 원본 전체를 공개 레포에 복사하지 않습니다.

- `data/source-dataset.md`: 원본 데이터셋 출처, 검증 요약, 사용 경계
- `data/p0/dataset-persona-map.json`: `demo-user-001 -> P001`, `peer-portfolio-023 -> P003` 매핑
- `data/p0/normalization-map.json`: 원본 feature 값과 FinMate seed 정규화 값의 관계
- `data/p0/feature-matrix.demo.csv`, `data/p0/ledger-sample.demo.csv`: P0 설명용 최소 subset

FinMate 화면과 seed 수치는 7분 발표 흐름에 맞게 정규화되어 있으며, 원본 ledger 전체를 그대로 재생하는 목적이 아닙니다.
이 레포는 제품 앱 코드와 함께 계약/문서/seed/검증 패키지를 유지합니다.

## P0 데모 범위

현재 공개 기준선은 **v1.0.0**입니다. P0는 7분 최종성과발표회에서 안정적으로 보여줄 핵심 흐름만 포함합니다.

| 포함 | 제외 |
| --- | --- |
| 30초 진단 | 실제 마이데이터 연동 |
| mock 마이데이터 동의 | 실사용 결제/송금 |
| 홈 금융 요약 | 투자상품 추천 |
| 또래 포트폴리오 상세 | LLM 필수 호출 |
| 1:1 비교와 `mainGap` | Birthday Wish Money P0 구현 |
| 3개월 시뮬레이션 | 포인트 원장 |
| 오늘의 미션 생성 | 친구 피드 원천 금융정보 노출 |
| 개인정보 공개 미리보기/철회 | 신규 P0 API 추가 |

## P1 앱 경험 확장

P1/v1.1은 P0 계약을 깨지 않고, 레퍼런스 기반 모바일 앱 경험을 더 넓게 보여주기 위한 mock 확장입니다.

- 5탭 구조: `홈 / 비교 / 미션 / 기록 / 프로필`
- 상세 흐름: 홈 카드 상세, 비교 필터, 비교 결과, AI 코치, 미션 피드백, 기록 히스토리, 프로필 섹션
- 소셜/임팩트 흐름: 친구 생일 알림, 생일펀드 참여, 내 생일펀드 열기/공유/현황
- 계약 파일: `openapi/finmate-p1-v1.1.yaml`
- 화면 seed: `seed/app-experience.json`
- 검증 스크립트: `scripts/validate_app_contract.py`

P1의 생일펀드는 발표용 social impact mock flow입니다. 실제 금액 이동, 결제, 송금, 알림 발송은 하지 않습니다.

## Product MVP

제품형 MVP 기준선은 **v1.2.0**입니다.

- 인증: `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/users/me`
- 상태 저장: Postgres + Flyway migration
- 앱 기능: 사용자별 홈, 미션 완료, 기록, 프로필, 친구 피드, 포인트 지갑, 생일펀드 참여
- AI 경계: `FinancialSnapshotV1 -> CoachResultV1`, `RuleBasedFallbackCoachProvider`
- 계약 파일: `openapi/finmate-v1.2-product-mvp.yaml`
- 검증 스크립트: `scripts/validate_product_mvp.py`

## Product Runtime Hardening

v1.3 실행 기준은 “새 사람이 레포를 받아 한 번에 실행하고 끝까지 검증할 수 있는 상태”입니다.

- Docker 실행: `postgres`, `api`, `web`을 `docker-compose.yml`에서 함께 실행
- DB reset: `scripts/reset-product-db.sh`
- 테스트 계정 bootstrap: `scripts/bootstrap-test-account.sh`
- E2E: `npm run e2e --prefix apps/web`
- Docker web: production build를 nginx로 서빙

구현된 기능:

- 회원가입/로그인/로그아웃
- 온보딩 완료 후 사용자별 앱 상태 저장
- 홈/비교/미션/기록/프로필 5탭
- 미션 완료와 포인트 적립
- 친구 피드와 생일펀드 참여
- refresh cookie 기반 로그인 유지

아직 구현하지 않은 기능:

- 실제 금융기관 MyData 연동
- 외부 AI/LLM provider 연결
- 실제 결제/송금/정산
- 카카오/네이버/구글 소셜 로그인
- 실제 푸시 알림

## Frozen Contract

- 공개 P0 API는 12개로 고정합니다.
- 로드맵 결정 없이 신규 P0 API를 추가하지 않습니다.
- `POST /api/coach/recommendations`는 공개 P0 API가 아닙니다.
- P0 추천은 rule-based 기본이며, LLM은 선택 고도화입니다.
- Birthday Wish Money는 P2 Social Impact 확장 사례로만 다룹니다.
- 근거 슬라이드에는 실제 확보 전까지 임의 수치를 넣지 않습니다.

## Repository Structure

```text
finmate/
  README.md
  build.gradle
  settings.gradle
  gradlew
  apps/
    web/
      src/
      package.json
    api/
      src/main/java/
      src/test/java/
  data/
    source-dataset.md
    p0/
      dataset-persona-map.json
      normalization-map.json
      feature-matrix.demo.csv
      ledger-sample.demo.csv
  .github/
    workflows/
      contract-check.yml
  docs/
    00_project_overview.md
    01_presentation_plan_v1.0.md
    02_screen_state_spec_v1.0.md
    03_api_handoff_v1.0.md
    04_erd_data_dictionary_v1.0.md
    05_roadmap_decision_criteria_v1.0.md
    06_mock_data_mapping_v1.0.md
    07_demo_rehearsal_script.md
    08_qa_checklist.md
    09_glossary.md
    10_evidence_collection_plan.md
  openapi/
    finmate-p0-v1.0.yaml
    finmate-p1-v1.1.yaml
    finmate-v1.2-product-mvp.yaml
  seed/
    app-experience.json
  requests/
    finmate-p0-v1.0.http
  scripts/
    validate_contract.py
    validate_app_contract.py
    validate_product_mvp.py
    demo-reset.md
  tasks/
    development_backlog_v1.0.md
```

## Run With Docker

제품 앱 전체를 실행합니다.

```bash
docker compose up --build
```

브라우저에서 `http://localhost:5173/login`을 열고 회원가입부터 시작합니다.

상태를 초기화하려면 API가 실행 중인 상태에서 아래 명령을 사용합니다.

```bash
scripts/reset-product-db.sh
scripts/bootstrap-test-account.sh
```

기본 테스트 계정:

```text
minjun@finmate.local / password123!
```

## Run For Local Development

Postgres만 Docker로 실행하고 API/Web은 로컬 dev server로 띄웁니다.

```bash
docker compose up -d postgres
FINMATE_DEV_TOOLS_ENABLED=true ./gradlew :apps:api:bootRun
npm ci --prefix apps/web
npm run dev --prefix apps/web -- --host 0.0.0.0
```

## Run The Legacy Mock API

Spring Boot mock API는 repo root의 `seed/*.json`을 읽어 P0 12개 API를 메모리 상태로 제공합니다. seed 파일은 수정하지 않고, 개인정보 철회나 mission idempotency 같은 상태 변화는 서버 실행 중 메모리에서만 처리합니다.

```bash
./gradlew :apps:api:bootRun
```

Health check:

```bash
curl http://localhost:8080/health
```

수동 리허설은 `requests/finmate-p0-v1.0.http`를 `@baseUrl = http://localhost:8080` 기준으로 순서대로 실행합니다. `GET /health`는 운영 확인용 endpoint이며 OpenAPI P0 12개 API에는 포함하지 않습니다.

`Idempotency-Key` header는 P0 request template에서 제품형 계약 참고값으로 남깁니다. 현재 Spring Boot mock runtime은 header를 저장하지 않고, mission 생성만 `simulationId + missionTemplateId + localDate` 기준으로 중복 생성을 막습니다.

프론트 연결 기준:

- API base URL은 `http://localhost:8080`입니다.
- CORS는 `localhost`와 `127.0.0.1`의 `3000`, `5173` origin을 허용합니다.
- P0 mock API는 발표 안정성을 위해 고정 입력에 strict합니다. ONB-02 선택값과 request payload는 `requests/finmate-p0-v1.0.http`의 값과 맞춰야 합니다.

## Run The Web App

API 서버를 먼저 실행한 뒤 별도 터미널에서 Vite web app을 실행합니다.

```bash
./gradlew :apps:api:bootRun
```

```bash
npm ci --prefix apps/web
npm run dev --prefix apps/web
```

브라우저에서 P1 앱은 `http://localhost:5173/home`으로 확인합니다.

```text
/home
-> /compare
-> /compare/filter
-> /compare/result
-> /compare/coach
-> /missions
-> /records
-> /profile
-> /birthdays
```

P0 회귀 흐름은 `http://localhost:5173/onboarding`에서 확인합니다.

```text
/onboarding
-> /home
-> /explore/portfolios/peer-portfolio-023
-> /explore/compare/peer-portfolio-023
-> /simulations/cmp-001
-> /missions/new/sim-001
-> /settings/privacy
```

web app은 `VITE_API_BASE_URL`이 없으면 `http://localhost:8080`을 사용합니다.

## Role-Based Reading Guide

| 역할 | 먼저 읽을 문서 |
| --- | --- |
| 발표자 | `docs/00_project_overview.md`, `docs/01_presentation_plan_v1.0.md`, `docs/07_demo_rehearsal_script.md` |
| 프론트엔드 | `apps/web/src`, `apps/web/README.md`, `openapi/finmate-p0-v1.0.yaml`, `openapi/finmate-p1-v1.1.yaml`, `requests/finmate-p0-v1.0.http` |
| 백엔드 | `docs/03_api_handoff_v1.0.md`, `docs/04_erd_data_dictionary_v1.0.md`, `seed/README.md` |
| 데이터/분석 | `data/source-dataset.md`, `data/p0/dataset-persona-map.json`, `docs/06_mock_data_mapping_v1.0.md` |
| QA/데모 운영 | `docs/08_qa_checklist.md`, `scripts/demo-reset.md`, `scripts/validate_contract.py`, `scripts/validate_app_contract.py` |
| 기획/팀 리드 | `docs/05_roadmap_decision_criteria_v1.0.md`, `docs/09_glossary.md`, `docs/10_evidence_collection_plan.md` |

## Validate The Contract

```bash
python3 scripts/validate_contract.py
python3 scripts/validate_app_contract.py
python3 scripts/validate_product_mvp.py
npm run e2e --prefix apps/web
```

성공 문구:

```text
FinMate P0 contract validation passed
FinMate P1 app contract validation passed
FinMate product MVP validation passed
```

검증 스크립트는 P0 공개 API 12개, P1 앱 경험 API, seed 고정 ID, 시뮬레이션 수치, 개인정보 철회 대상, 화면 응답 필드, 데이터셋 subset 경계, 금지된 버전/표현 회귀를 확인합니다.

같은 검증과 `./gradlew :apps:api:test`는 GitHub Actions의 `Contract Check` workflow에서도 `push`와 `pull_request`마다 실행됩니다.

## Rehearse The Demo

1. `./gradlew :apps:api:bootRun`으로 mock API를 실행합니다.
2. `curl http://localhost:8080/health`로 서버 상태를 확인합니다.
3. `requests/finmate-p0-v1.0.http`의 12단계 P0 흐름과 개인정보 공개/철회 검증을 순서대로 확인합니다.
4. 발표 리허설은 `docs/07_demo_rehearsal_script.md`의 7분 타임박스를 따릅니다.
5. 최종 점검은 `docs/08_qa_checklist.md`로 진행합니다.

## Versioning

이 레포의 공개 기준선은 `v1.0.0`입니다. 이전 내부 작업 번호는 공개 문서에서 사용하지 않습니다.
