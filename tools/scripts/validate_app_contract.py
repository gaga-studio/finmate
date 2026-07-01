#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None


ROOT = Path(__file__).resolve().parents[2]
OPENAPI_PATH = ROOT / "contracts" / "openapi" / "legacy" / "finmate-p1-v1.1.yaml"
SEED_PATH = ROOT / "fixtures" / "app-seed" / "app-experience.json"
EXPECTED_PATHS = {
    "/api/app/home",
    "/api/app/home/{detail}",
    "/api/app/compare",
    "/api/app/compare/filter",
    "/api/app/compare/filter/search",
    "/api/app/compare/results/{comparisonId}",
    "/api/app/compare/{comparisonId}/coach-flow",
    "/api/app/missions",
    "/api/app/missions/add",
    "/api/app/missions/add/{templateId}",
    "/api/app/missions/{missionId}",
    "/api/app/missions/{missionId}/feedback",
    "/api/app/records",
    "/api/app/records/{date}",
    "/api/app/profile",
    "/api/app/profile/sections/{section}",
    "/api/app/birthdays",
    "/api/app/birthdays/{birthdayId}/flow",
    "/api/app/birthday-funds/{fundId}/contributions",
    "/api/app/birthday-funds/{fundId}/complete",
    "/api/app/birthday-funds/me/open",
    "/api/app/birthday-funds/me/share",
    "/api/app/birthday-funds/me/status",
}
EXPECTED_OPERATION_COUNT = 25
EXPECTED_SCREEN_IDS = {
    "home",
    "home:mission",
    "home:budget",
    "home:spending",
    "home:assets",
    "home:following",
    "compare",
    "compare:filter",
    "compare:filter-results",
    "compare:cmp-001",
    "compare:coach-flow",
    "missions",
    "missions:mission-food",
    "missions:mission-invest",
    "missions:mission-fixed-cost",
    "missions:feedback",
    "missions:next-goals",
    "records:2026-06",
    "records:2026-06-12",
    "records:history",
    "records:stats",
    "profile",
    "profile:followers",
    "profile:following",
    "profile:activities",
    "profile:privacy",
    "birthdays",
    "birthdays:bday-jiwoo",
    "birthday-funds:fund-jiwoo:status",
    "birthday-funds:me:open",
    "birthday-funds:me:share",
    "birthday-funds:me:status",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def load_yaml(path: Path) -> dict:
    if yaml is None:
        return {}
    with path.open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def load_seed(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def collect_operation_ids(openapi: dict) -> list[str]:
    methods = {"get", "post", "patch", "put", "delete"}
    operation_ids: list[str] = []
    for path_item in openapi.get("paths", {}).values():
        for method, operation in path_item.items():
            if method.lower() in methods:
                operation_ids.append(operation.get("operationId"))
    return operation_ids


def main() -> int:
    openapi = load_yaml(OPENAPI_PATH)
    seed = load_seed(SEED_PATH)

    openapi_text = OPENAPI_PATH.read_text(encoding="utf-8")
    if openapi:
        version = openapi.get("info", {}).get("version")
        paths = set(openapi.get("paths", {}).keys())
        operation_ids = collect_operation_ids(openapi)
    else:
        version_match = re.search(r"^\s+version:\s+([0-9.]+)\s*$", openapi_text, re.MULTILINE)
        version = version_match.group(1) if version_match else None
        paths = set(re.findall(r"^\s{2}(/api/app/[^:]+):\s*$", openapi_text, re.MULTILINE))
        operation_ids = re.findall(r"^\s+operationId:\s+([A-Za-z0-9_]+)\s*$", openapi_text, re.MULTILINE)

    if version != "1.1.0":
        fail("P1 OpenAPI version must be 1.1.0")

    missing_paths = sorted(EXPECTED_PATHS - paths)
    extra_paths = sorted(paths - EXPECTED_PATHS)
    if missing_paths:
        fail(f"Missing P1 paths: {missing_paths}")
    if extra_paths:
        fail(f"Unexpected P1 paths: {extra_paths}")

    if len(operation_ids) != EXPECTED_OPERATION_COUNT:
        fail(f"Expected {EXPECTED_OPERATION_COUNT} P1 operations, found {len(operation_ids)}")
    if len(set(operation_ids)) != len(operation_ids) or any(not operation_id for operation_id in operation_ids):
        fail("Every P1 operation must have a unique operationId")

    screen_ids = {item.get("id") for item in seed}
    missing_screens = sorted(EXPECTED_SCREEN_IDS - screen_ids)
    if missing_screens:
        fail(f"Missing P1 seed screens: {missing_screens}")

    for screen in seed:
        for field in ["id", "screenId", "title", "tab", "statusBarTime", "sections", "meta"]:
            if field not in screen:
                fail(f"Screen {screen.get('id')} missing {field}")
        if not isinstance(screen["sections"], list) or not screen["sections"]:
            fail(f"Screen {screen['id']} must contain at least one section")
        for section in screen["sections"]:
            for field in ["id", "kind", "title"]:
                if field not in section:
                    fail(f"Screen {screen['id']} has section missing {field}")

    birthday_complete = next(item for item in seed if item["id"] == "birthday-funds:fund-jiwoo:status")
    if "참여 완료" not in birthday_complete["title"]:
        fail("Birthday contribution completion screen must be present")

    print("FinMate P1 app contract validation passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"Contract validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
