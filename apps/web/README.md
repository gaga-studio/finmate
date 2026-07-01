# FinMate P0 모바일 앱 프로토타입

Vite React 기반의 P0 모바일 앱 화면입니다.  
비상금 루틴을 중심으로 `또래 비교 → 시뮬레이션 → 미션 생성 → 공개 범위 확인` 흐름을 검증합니다.

화면은 금융 앱의 신뢰감, 데이터 인사이트, 카드형 탐색 경험을 참고하되 새 API나 새 seed 없이 기존 P0 응답만 사용합니다.

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
http://localhost:5173/onboarding
http://127.0.0.1:5173/onboarding
```

기본 API 주소는 `http://localhost:8080`입니다. 다른 주소를 사용할 때는 `VITE_API_BASE_URL`을 설정합니다.

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev --prefix apps/web
```

## 주요 흐름

```text
/onboarding
/home
/explore/portfolios/:id
/explore/compare/:portfolioId
/simulations/:comparisonId
/missions/new/:simulationId
/settings/privacy
```

## 스크린샷

대표 화면 캡처는 `docs/assets/screenshots/`에 저장합니다.

```text
onboarding.png
home.png
compare.png
simulation.png
privacy-withdraw.png
```
