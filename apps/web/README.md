# FinMate P0 Mobile App Prototype

Vite React 기반 P0 모바일 앱 프로토타입입니다. Spring Boot mock API가 먼저 실행되어야 합니다.

이 화면은 `하나/핀크식 금융 신뢰감`, `뱅크샐러드식 데이터 인사이트`, `인스타그램식 카드 탐색`을 참고하되, FinMate의 핵심 흐름인 `비교 → 시뮬레이션 → 미션`에 맞춰 구성했습니다.

```bash
cd ../..
./gradlew :apps:api:bootRun
```

```bash
npm ci --prefix apps/web
npm run dev --prefix apps/web
```

브라우저에서 `http://localhost:5173/onboarding`을 열면 온보딩부터 시작할 수 있습니다.
로컬 환경에 따라 `http://127.0.0.1:5173/onboarding`으로도 같은 흐름을 확인할 수 있습니다.

기본 API 주소는 `http://localhost:8080`입니다. 다른 주소를 쓸 때는 `VITE_API_BASE_URL`을 설정합니다.

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev --prefix apps/web
```

주요 route:

```text
/onboarding
/home
/explore/portfolios/:id
/explore/compare/:portfolioId
/simulations/:comparisonId
/missions/new/:simulationId
/settings/privacy
```

## Screenshots

대표 화면 캡처는 `docs/assets/screenshots/`에 저장합니다.

```text
onboarding.png
home.png
compare.png
simulation.png
privacy-withdraw.png
```
