# FinMate 모바일 웹/PWA 앱

React + Vite 기반의 FinMate 모바일 웹앱입니다. 사용자는 회원가입/로그인 후 온보딩, 홈, 비교, 미션, 기록, 프로필, 친구 피드, 생일펀드, 포인트 지갑 흐름을 사용할 수 있습니다.

## Docker 실행

repo root에서 제품 앱 전체를 실행합니다.

```bash
docker compose up --build
```

시작 경로:

```text
http://localhost:5173/login
```

## 로컬 개발 실행

```bash
docker compose up -d postgres
FINMATE_DEV_TOOLS_ENABLED=true ./gradlew :apps:api:bootRun
npm ci --prefix apps/web
npm run dev --prefix apps/web -- --host 0.0.0.0
```

기본 API 주소는 `http://localhost:8080`입니다. 다른 주소를 사용할 때는 `VITE_API_BASE_URL`을 설정합니다.

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev --prefix apps/web
```

## 테스트 계정

```text
minjun@finmate.local / password123!
```

API가 실행 중일 때 아래 명령으로 DB 상태를 초기화하고 테스트 계정을 다시 만들 수 있습니다.

```bash
scripts/reset-product-db.sh
scripts/bootstrap-test-account.sh
```

## 주요 흐름

```text
/signup
/login
/onboarding
/home
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
```

## 검증

```bash
npm run lint --prefix apps/web
npm run build --prefix apps/web
npm run e2e --prefix apps/web
python3 scripts/validate_product_mvp.py
```

E2E는 `http://localhost:5173`와 `http://localhost:8080`이 실행 중이라고 가정합니다. 다른 주소를 사용할 때는 `PLAYWRIGHT_BASE_URL`, `PLAYWRIGHT_API_URL`을 설정합니다.

PWA manifest는 `public/manifest.webmanifest`, 기본 service worker는 `public/sw.js`에 있습니다. Docker web은 Vite production build를 nginx로 서빙합니다.

## 스크린샷

대표 화면 캡처는 `docs/assets/screenshots/`에서 확인합니다.

- `product-home.png`
- `product-birthday-complete.png`
- `product-profile.png`
