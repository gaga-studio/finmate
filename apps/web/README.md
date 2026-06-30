# FinMate Web Skeleton

Vite React 기반 P0 클릭 데모입니다. Spring Boot mock API가 먼저 실행되어야 합니다.

```bash
cd ../..
./gradlew :apps:api:bootRun
```

```bash
npm ci --prefix apps/web
npm run dev --prefix apps/web
```

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
