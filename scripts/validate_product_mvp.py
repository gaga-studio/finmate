#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

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
    openapi = read("openapi/finmate-v1.2-product-mvp.yaml")
    migration = read("apps/api/src/main/resources/db/migration/V1__product_mvp_schema.sql").lower()
    controller = read("apps/api/src/main/java/com/gagastudio/finmate/api/controller/FinmateController.java")
    api_client = read("apps/web/src/api.ts")
    app = read("apps/web/src/App.tsx")
    manifest = read("apps/web/public/manifest.webmanifest")

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
        if route not in app:
            fail(f"Missing frontend auth flow snippet: {route}")

    if "display\": \"standalone\"" not in manifest:
        fail("PWA manifest must use standalone display mode")

    if "const DEMO_ACCESS_TOKEN" in api_client:
        fail("Frontend app API must not default to demo-token")

    print("FinMate product MVP validation passed")


if __name__ == "__main__":
    main()
