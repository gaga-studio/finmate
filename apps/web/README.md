# FinMate 모바일 웹/PWA 앱

React + Vite 기반의 FinMate 모바일 웹앱입니다. 사용자는 회원가입/로그인 후 온보딩, 홈, 비교, 미션, 기록, 프로필, 친구 피드, 생일펀드, 포인트 지갑 흐름을 사용할 수 있습니다.

## 실행

```bash
docker compose up -d postgres
./gradlew :apps:api:bootRun
npm ci --prefix apps/web
npm run dev --prefix apps/web -- --host 0.0.0.0
```

시작 경로:

```text
http://localhost:5173/login
```

기본 API 주소는 `http://localhost:8080`입니다. 다른 주소를 사용할 때는 `VITE_API_BASE_URL`을 설정합니다.

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev --prefix apps/web
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
python3 scripts/validate_product_mvp.py
```

PWA manifest는 `public/manifest.webmanifest`, 기본 service worker는 `public/sw.js`에 있습니다.

## 스크린샷

대표 화면 캡처는 `docs/assets/screenshots/`에서 확인합니다.

- `product-home.png`
- `product-birthday-complete.png`
- `product-profile.png`
