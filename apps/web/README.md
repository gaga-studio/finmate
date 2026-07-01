# FinMate P0 모바일 앱 프로토타입

Vite React 기반의 P0 모바일 앱 화면입니다.  
`홈 / 비교 / 미션 / 기록 / 프로필` 5개 탭으로 금융 습관 앱의 핵심 화면을 검증합니다.

화면은 제공된 서비스 기획 레퍼런스를 기준으로 보라색 중심의 카드형 모바일 UI에 가깝게 구성했습니다. 새 API나 새 seed 없이 기존 P0 API와 프론트 데모 데이터를 함께 사용합니다.

## 실행 방법

Spring Boot mock API를 먼저 실행합니다.

```bash
cd ../..
./gradlew :apps:api:bootRun
```

다른 터미널에서 웹 앱을 실행합니다.

```bash
npm ci --prefix apps/web
npm run dev --prefix apps/web
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

## 주요 흐름

```text
/onboarding
/home
/compare
/compare/filter
/missions
/records
/profile
/settings/privacy
```

기존 P0 호환 경로인 `/explore/compare/:portfolioId`, `/simulations/:comparisonId`, `/missions/new/:simulationId`도 유지합니다.

## 스크린샷

대표 화면 캡처는 `docs/assets/screenshots/`에 저장합니다.

```text
onboarding.png
home.png
compare.png
mission.png
records.png
profile.png
```
