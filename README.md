# FinMate

FinMate는 청년 사용자가 금융 루틴을 만들고, 또래와 비교하고, 미션과 기록으로 행동을 이어가도록 돕는 모바일 웹/PWA 앱입니다.

현재 레포의 기준은 **제품형 MVP**입니다. 이메일 회원가입/로그인, 온보딩, 홈, 비교, 미션, 기록, 프로필, 친구 피드, 생일펀드, 포인트 원장이 동작하며 사용자별 상태는 Postgres에 저장됩니다.

실제 금융기관 MyData, 실제 결제/송금/정산, 실제 푸시, 외부 AI provider는 아직 연결하지 않았습니다. MyData/AI는 계약 경계만 열어두고, 앱은 rule-based fallback으로 끝까지 사용할 수 있게 구성되어 있습니다.

## 빠른 실행

처음 실행은 Docker 기준을 권장합니다.

```bash
docker compose up --build
```

브라우저에서 아래 주소를 엽니다.

```text
http://localhost:5173/login
```

API 상태는 아래 주소에서 확인합니다.

```text
http://localhost:8080/health
```

## 테스트 계정

API가 실행 중일 때 테스트 계정을 다시 만들 수 있습니다.

```bash
tools/scripts/reset-product-db.sh
tools/scripts/bootstrap-test-account.sh
```

기본 계정:

```text
minjun@finmate.local / password123!
```

새로 회원가입한 사용자는 빈 상태에서 시작합니다. 위 bootstrap 계정은 발표/캡처/QA용으로 미션, 예산, 자산, 친구 피드, 생일 이벤트가 들어 있습니다.

## 주요 흐름

```text
/signup
-> /onboarding
-> /home
-> /compare
-> /missions
-> /records
-> /profile
```

상세 흐름:

```text
비교 -> AI 코치 -> 행동 계획 -> 미션
미션 완료 -> 포인트 적립 -> 기록 반영
친구 생일 알림 -> 생일펀드 참여 -> 포인트 원장 반영
프로필 -> 공개 범위 확인 -> 로그아웃
```

## 현재 되는 것

| 영역 | 상태 |
| --- | --- |
| 인증 | 이메일/비밀번호 회원가입, 로그인, refresh cookie, 로그아웃 |
| 온보딩 | 30초 설문, 개인정보 공개 동의, 마이데이터 제공 동의 저장 |
| 앱 화면 | 홈, 비교, 미션, 기록, 프로필 5탭 |
| 소셜 | 친구 피드, 팔로잉/팔로워 요약, 생일 이벤트 |
| 포인트 | 미션 완료/생일펀드 참여 기반 가상 포인트 원장 |
| AI 경계 | `FinancialSnapshotV1 -> CoachResultV1`, rule-based fallback |
| 실행 | Docker Compose로 Postgres/API/Web 동시 실행 |

## 아직 안 되는 것

- 실제 금융기관 MyData 연동
- 실제 결제, 송금, 정산
- 외부 AI/LLM provider 연결
- 카카오/네이버/구글 소셜 로그인
- 실제 푸시 알림

## 디렉터리 구조

```text
finmate/
  apps/
    api/                  Spring Boot API, auth, DB, app services
    web/                  React/Vite 모바일 웹/PWA
  contracts/
    openapi/current/      현재 제품형 MVP OpenAPI
    openapi/legacy/       P0/P1 발표/확장 계약 보관
    http/legacy/          과거 P0 수동 요청 파일
  docs/
    product/              현재 제품 기능과 사용자 흐름
    architecture/         레포 구조, API, 데이터 흐름
    qa/                   실행/검증/QA 기준
    archive/              P0/P1 발표·기획 자료 보관
    assets/screenshots/   선별된 화면 캡처
  fixtures/
    app-seed/             개발/데모 bootstrap seed
    mydata-samples/       합성 MyData 출처 연결용 최소 subset
  tools/
    scripts/              reset, bootstrap, validation scripts
```

현재 제품 계약은 `contracts/openapi/current/finmate-v1.2-product-mvp.yaml`을 기준으로 봅니다. 과거 P0/P1 계약은 `contracts/openapi/legacy/`에 보관합니다.

앱 seed는 `fixtures/app-seed`에 있고, 원본 합성 데이터셋과의 연결 자료는 `fixtures/mydata-samples`에 있습니다. 검증과 운영 보조 명령은 `tools/scripts`에 모아둡니다.

## 로컬 개발

Postgres만 Docker로 띄우고 API/Web은 로컬 개발 서버로 실행할 수 있습니다.

```bash
docker compose up -d postgres
FINMATE_DEV_TOOLS_ENABLED=true ./gradlew :apps:api:bootRun
npm ci --prefix apps/web
npm run dev --prefix apps/web -- --host 0.0.0.0
```

기본 API 주소는 `http://localhost:8080`입니다. 프론트에서 다른 API 주소를 보려면 `VITE_API_BASE_URL`을 설정합니다.

## 검증 명령

```bash
python3 tools/scripts/validate_contract.py
python3 tools/scripts/validate_app_contract.py
python3 tools/scripts/validate_product_mvp.py
./gradlew :apps:api:test
npm run lint --prefix apps/web
npm run build --prefix apps/web
npm run e2e --prefix apps/web
```

E2E는 `http://localhost:5173`와 `http://localhost:8080`이 실행 중이라고 가정합니다. 다른 주소를 사용할 때는 `PLAYWRIGHT_BASE_URL`, `PLAYWRIGHT_API_URL`을 설정합니다.

## 문서 맵

| 문서 | 내용 |
| --- | --- |
| `docs/README.md` | 전체 문서 안내 |
| `docs/product/current-mvp.md` | 현재 제품형 MVP 범위 |
| `docs/architecture/repository-map.md` | 디렉터리와 데이터 흐름 |
| `docs/qa/testing.md` | 실행, 검증, QA 방법 |
| `docs/archive/p0-p1-planning/` | 과거 P0/P1 발표·기획 자료 |
| `DESIGN.md` | 화면 디자인 토큰과 UI 원칙 |

## 데이터 사용 경계

FinMate의 개발 seed는 합성 청년 금융 데이터셋 `gaga-studio/financial-sns-mydata-202606`을 참고합니다. 이 레포에는 원본 전체 데이터, 전체 거래 원장, Excel 산출물, 실제 개인 금융정보를 포함하지 않습니다.

공개 레포에 포함된 데이터는 제품 동작과 설명에 필요한 최소 fixture입니다.
