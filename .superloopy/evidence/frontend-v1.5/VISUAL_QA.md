# FinMate v1.5 Visual QA

## Design Direction
- Product signal changed from forced violet to deep teal/green after user steering.
- Violet remains only as a restrained AI coach and peer-comparison accent.
- Design tokens are recorded in `DESIGN.md` and summarized in `DESIGN_TOKENS.md`.

## Browser Evidence
- `signup-390.png`
- `onboarding-survey-390.png`
- `onboarding-consent-390.png`
- `home-390.png`
- `compare-390.png`
- `coach-390.png`
- `mission-390.png`
- `records-390.png`
- `profile-390.png`
- `home-768.png`
- `home-1280.png`

## QA Notes
- 390x844 mobile viewport: auth, onboarding, home, compare, coach, mission, records, and profile screenshots captured from real browser.
- 768px and 1280px desktop viewports: centered mobile shell verified on home.
- Bottom navigation remains `홈 / 비교 / 미션 / 기록 / 프로필` and does not overlap primary visible controls in captured screens.
- User-facing technical copy regression covered by Playwright E2E `expectNoTechnicalCopy`.
- Product flow verified with Docker web/API/Postgres running.

## Commands
- `npm run lint --prefix apps/web`
- `npm run build --prefix apps/web`
- `./gradlew :apps:api:test`
- `python3 scripts/validate_contract.py`
- `python3 scripts/validate_app_contract.py`
- `python3 scripts/validate_product_mvp.py`
- `docker compose up -d --build`
- `npm run e2e --prefix apps/web`

## Anti-Slop Preflight
- Zero visible em-dashes in updated UI copy.
- No forced purple gradient system. Primary UI uses teal/green.
- Non-default Korean-first font stack is declared.
- Color, shape, and theme are consistent through `DESIGN.md` tokens.
- Real app character assets are used, no competitor logos or external brand assets.
- Loading, empty/error, auth, onboarding, and app screens use product-facing copy.
