#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

EXPECTED_CONTROLLER_SNIPPETS = [
    'GetMapping("/api/app/home")',
    'GetMapping("/api/app/home/{detail}")',
    'GetMapping("/api/app/compare")',
    'GetMapping("/api/app/compare/filter")',
    'PostMapping("/api/app/compare/filter/search")',
    'GetMapping("/api/app/compare/results/{comparisonId}")',
    'GetMapping("/api/app/compare/{comparisonId}/coach-flow")',
    'GetMapping("/api/app/missions")',
    'GetMapping("/api/app/missions/add")',
    'PostMapping("/api/app/missions/add/{templateId}")',
    'GetMapping("/api/app/missions/{missionId}")',
    'GetMapping("/api/app/records")',
    'GetMapping("/api/app/records/{date}")',
    'GetMapping("/api/app/profile")',
    'GetMapping("/api/app/profile/sections/{section}")',
    'GetMapping("/api/app/birthdays")',
    'GetMapping("/api/app/birthdays/{birthdayId}/flow")',
    'PostMapping("/api/app/birthday-funds/{fundId}/contributions")',
    'GetMapping("/api/app/birthday-funds/{fundId}/complete")',
    'GetMapping("/api/app/birthday-funds/me/open")',
    'PostMapping("/api/app/birthday-funds/me/open")',
    'GetMapping("/api/app/birthday-funds/me/share")',
    'PostMapping("/api/app/birthday-funds/me/share")',
    'GetMapping("/api/app/birthday-funds/me/status")',
]

EXPECTED_WEB_SNIPPETS = [
    "getAppHome",
    "getAppCompare",
    "getAppMissions",
    "getAppMissionAdd",
    "addAppMissionFromTemplate",
    "getAppRecords",
    "getAppProfile",
    "getAppBirthdays",
    "contributeBirthdayFund",
]

FORBIDDEN_RUNTIME_SNIPPETS = [
    "/api/app/missions/${missionId}/feedback",
    "/api/app/missions/{missionId}/feedback",
    "submitAppMissionFeedback",
    "mission-feedback",
    "fund-jiwoo",
    "bday-jiwoo",
    "demo-token",
]


def fail(message: str) -> None:
    raise AssertionError(message)


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def main() -> None:
    controller = read("apps/api/src/main/java/com/gagastudio/finmate/api/controller/FinmateController.java")
    product_service = read("apps/api/src/main/java/com/gagastudio/finmate/api/product/ProductAppService.java")
    api_client = read("apps/web/src/api.ts")
    navigation = read("apps/web/src/navigation.ts")
    app_screen = read("apps/web/src/AppScreenPage.tsx")

    for snippet in EXPECTED_CONTROLLER_SNIPPETS:
        if snippet not in controller:
            fail(f"Controller missing current app API: {snippet}")

    for snippet in EXPECTED_WEB_SNIPPETS:
        if snippet not in api_client:
            fail(f"Frontend API client missing wrapper: {snippet}")

    runtime_text = "\n".join([controller, product_service, api_client, navigation, app_screen])
    for snippet in FORBIDDEN_RUNTIME_SNIPPETS:
        if snippet in runtime_text:
            fail(f"Runtime still contains legacy/demo snippet: {snippet}")

    if "MissionEvaluationService" not in product_service:
        fail("ProductAppService must use behavior-data mission evaluation")
    if "행동 데이터" not in product_service:
        fail("Mission detail copy must explain behavior-data based verification")

    print("FinMate current app contract validation passed")


if __name__ == "__main__":
    main()
