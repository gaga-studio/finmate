#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

EXPECTED_PATHS = {
    "/api/auth/signup",
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/logout",
    "/api/users/me",
    "/api/users/me/onboarding",
    "/api/ai/financial-snapshot",
    "/api/ai/coach-results/fallback",
    "/api/ai/coach-results",
}

EXPECTED_TABLES = {
    "users",
    "refresh_tokens",
    "user_profiles",
    "privacy_settings",
    "onboarding_responses",
    "consent_events",
    "mydata_connections",
    "financial_snapshots",
    "coach_results",
    "missions",
    "mission_events",
    "daily_records",
    "friendships",
    "feed_items",
    "point_wallets",
    "point_transactions",
    "birthday_funds",
    "birthday_fund_contributions",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def main() -> None:
    openapi = read("contracts/openapi/current/finmate-v1.2-product-mvp.yaml")
    migration = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted((ROOT / "apps/api/src/main/resources/db/migration").glob("V*__*.sql"))
    ).lower()
    controller = read("apps/api/src/main/java/com/gagastudio/finmate/api/controller/FinmateController.java")
    api_client = read("apps/web/src/api.ts")
    app_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted((ROOT / "apps/web/src").glob("*.tsx"))
    )
    manifest = read("apps/web/public/manifest.webmanifest")
    compose = read("docker-compose.yml")
    web_package = read("apps/web/package.json")
    playwright_config = read("apps/web/playwright.config.ts")
    e2e = read("apps/web/e2e/product-flow.spec.ts")

    missing_paths = sorted(path for path in EXPECTED_PATHS if path not in openapi or path not in controller)
    if missing_paths:
        fail(f"Missing product MVP API paths: {missing_paths}")

    missing_tables = sorted(table for table in EXPECTED_TABLES if f"create table {table}" not in migration)
    if missing_tables:
        fail(f"Missing product MVP tables: {missing_tables}")

    for symbol in [
        "FinancialSnapshotV1",
        "CoachResultV1",
        "FinancialDataProvider",
        "CoachProvider",
        "RuleBasedFallbackCoachProvider",
        "ProductAppService",
        "AuthService",
        "JwtService",
    ]:
        if symbol not in "".join([
            read("apps/api/src/main/java/com/gagastudio/finmate/api/dto/ApiDtos.java"),
            read("apps/api/src/main/java/com/gagastudio/finmate/api/product/FinancialDataProvider.java"),
            read("apps/api/src/main/java/com/gagastudio/finmate/api/product/CoachProvider.java"),
            read("apps/api/src/main/java/com/gagastudio/finmate/api/product/RuleBasedFallbackCoachProvider.java"),
            read("apps/api/src/main/java/com/gagastudio/finmate/api/product/ProductAppService.java"),
            read("apps/api/src/main/java/com/gagastudio/finmate/api/auth/AuthService.java"),
            read("apps/api/src/main/java/com/gagastudio/finmate/api/auth/JwtService.java"),
        ]):
            fail(f"Missing backend product symbol: {symbol}")

    for snippet in ["credentials: 'include'", "signup:", "login:", "refresh:", "completeOnboarding"]:
        if snippet not in api_client:
            fail(f"Missing frontend API auth snippet: {snippet}")

    for route in ["login", "signup", "AuthPage", "clearSession", "api.logout"]:
        if route not in app_sources:
            fail(f"Missing frontend auth flow snippet: {route}")

    if "display\": \"standalone\"" not in manifest:
        fail("PWA manifest must use standalone display mode")

    if "const DEMO_ACCESS_TOKEN" in api_client:
        fail("Frontend app API must not default to demo-token")

    for path in [
        "apps/api/Dockerfile",
        "apps/web/Dockerfile",
        "apps/web/nginx.conf",
        ".dockerignore",
        "tools/scripts/reset-product-db.sh",
        "tools/scripts/bootstrap-test-account.sh",
    ]:
        if not (ROOT / path).exists():
            fail(f"Missing product runtime file: {path}")

    for snippet in ["api:", "web:", "condition: service_healthy", "FINMATE_DEV_TOOLS_ENABLED"]:
        if snippet not in compose:
            fail(f"docker-compose.yml missing product runtime snippet: {snippet}")

    for snippet in ["PostMapping(\"/api/dev/reset\")", "PostMapping(\"/api/dev/bootstrap-test-account\")", "resetDevelopmentState", "bootstrapTestAccount", "devToolsEnabled"]:
        if snippet not in controller + read("apps/api/src/main/java/com/gagastudio/finmate/api/product/ProductAppService.java"):
            fail(f"Missing development reset snippet: {snippet}")

    if '"e2e": "playwright test"' not in web_package or "@playwright/test" not in web_package:
        fail("apps/web/package.json must expose Playwright E2E")

    for snippet in ["viewport: { width: 390, height: 844 }", "PLAYWRIGHT_BASE_URL"]:
        if snippet not in playwright_config:
            fail(f"Playwright config missing: {snippet}")

    for snippet in [
        "/api/dev/reset",
        "/api/dev/bootstrap-test-account",
        "회원가입",
        "시작하기",
        "오늘의 지출 요약",
        "오늘 실천 기록하기",
        "축하 펀드 참여하기",
        "로그아웃",
        "expectNoTechnicalCopy",
    ]:
        if snippet not in e2e:
            fail(f"Product E2E missing: {snippet}")

    print("FinMate product MVP validation passed")


if __name__ == "__main__":
    main()
