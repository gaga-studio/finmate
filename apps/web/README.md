# FinMate 모바일 앱 프로토타입

Vite React 기반의 FinMate 모바일 앱 화면입니다.
`홈 / 비교 / 미션 / 기록 / 프로필` 5개 탭과 각 탭의 상세 화면, AI 코치 흐름, 생일펀드 mock flow를 검증합니다.

P0 공개 API 12개는 유지하고, 앱형 화면 데이터는 P1/v1.1 mock API(`GET /api/app/...`)에서 내려받습니다. 실제 금융 거래, 송금, 결제, 푸시는 포함하지 않습니다.

## 실행

Spring Boot mock API를 먼저 실행합니다.

```bash
./gradlew :apps:api:bootRun
```

다른 터미널에서 웹 앱을 실행합니다.

```bash
npm ci --prefix apps/web
npm run dev --prefix apps/web -- --host 0.0.0.0
```

시작 경로:

```text
http://localhost:5173/home
http://127.0.0.1:5173/home
```

기본 API 주소는 `http://localhost:8080`입니다. 다른 주소를 사용할 때는 `VITE_API_BASE_URL`을 설정합니다.

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev --prefix apps/web
```

## 주요 화면

```text
/home
/home/:detail
/compare
/compare/filter
/compare/result
/compare/coach
/missions
/missions/:missionId
/missions/:missionId/feedback
/records
/records/:date
/profile
/profile/:section
/birthdays
/birthdays/:birthdayId
/birthday-funds/:fundId/contribute
/birthday-funds/:fundId/complete
/birthday-funds/me/open
/birthday-funds/me/share
/birthday-funds/me/status
/settings/privacy
```

기존 P0 호환 경로인 `/onboarding`, `/explore/compare/:portfolioId`, `/simulations/:comparisonId`, `/missions/new/:simulationId`도 유지합니다.

## 검증

```bash
npm run lint --prefix apps/web
npm run build --prefix apps/web
python3 scripts/validate_app_contract.py
```

대표 화면 캡처는 `docs/assets/screenshots/`에 저장합니다.

```text
p1-home.png
p1-compare-result.png
p1-coach.png
p1-mission-fixed-cost.png
p1-mission-feedback.png
p1-birthday-flow.png
p1-birthday-contribute.png
p1-birthday-complete.png
p1-record-history.png
p1-profile-followers.png
```
