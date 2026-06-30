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


ROOT = Path(__file__).resolve().parents[1]
OPENAPI_PATH = ROOT / "openapi" / "finmate-p0-v1.0.yaml"
SEED_DIR = ROOT / "seed"
REQUEST_PATH = ROOT / "requests" / "finmate-p0-v1.0.http"

HTTP_METHODS = {"get", "post", "patch", "put", "delete", "options", "head", "trace"}
EXPECTED_RESPONSE_FIELDS = {
    "HomeResponse": ["goal", "peerTeaser", "todayMissionCandidate"],
    "PortfolioResponse": ["routineCards", "privacyBadges", "dataMode"],
    "ComparisonResponse": ["gapItems", "nextAction"],
    "SimulationResponse": ["insight", "nextAction", "disclaimer"],
    "MissionResponse": ["description", "difficulty", "verificationType", "privacySharePreview"],
    "PrivacySettingsResponse": ["exposedFields", "ownPortfolioId"],
}


def fail(message: str) -> None:
    raise AssertionError(message)


def load_yaml(path: Path) -> dict:
    if yaml is None:
        return {}
    with path.open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def collect_operations(openapi: dict) -> list[tuple[str, str, dict]]:
    operations: list[tuple[str, str, dict]] = []
    for path, path_item in openapi.get("paths", {}).items():
        for method, operation in path_item.items():
            if method.lower() in HTTP_METHODS:
                operations.append((method.lower(), path, operation))
    return operations


def schema_props(openapi: dict, schema_name: str) -> dict:
    schema = openapi["components"]["schemas"][schema_name]
    return schema.get("properties", {})


def assert_openapi_contract(openapi: dict) -> None:
    if openapi.get("info", {}).get("version") != "1.0.0":
        fail("OpenAPI info.version must be 1.0.0")

    paths = openapi.get("paths", {})
    if "/api/coach/recommendations" in paths:
        fail("POST /api/coach/recommendations must not be a public P0 API")

    operations = collect_operations(openapi)
    if len(operations) != 12:
        fail(f"Expected 12 public P0 operations, found {len(operations)}")

    operation_ids = [operation.get("operationId") for _, _, operation in operations]
    if len(set(operation_ids)) != 12 or any(not operation_id for operation_id in operation_ids):
        fail("Every operation must have a unique operationId")

    mission_props = schema_props(openapi, "MissionRequest")
    for field in ["simulationId", "missionTemplateId", "triggerSource", "recommendationSource", "difficulty"]:
        if field not in mission_props:
            fail(f"MissionRequest missing {field}")
    if "source" in mission_props:
        fail("MissionRequest must not contain source")

    patch_schema = openapi["components"]["schemas"]["PrivacySettingsPatchRequest"]
    if patch_schema.get("minProperties") != 1:
        fail("PrivacySettingsPatchRequest must have minProperties: 1")

    withdraw_schema = openapi["components"]["schemas"]["PrivacyWithdrawRequest"]
    if "oneOf" not in withdraw_schema or len(withdraw_schema["oneOf"]) != 2:
        fail("PrivacyWithdrawRequest must use oneOf with two request shapes")

    for schema_name, fields in EXPECTED_RESPONSE_FIELDS.items():
        props = schema_props(openapi, schema_name)
        for field in fields:
            if field not in props:
                fail(f"{schema_name} missing {field}")

    peer_teaser_props = schema_props(openapi, "HomeResponse")["peerTeaser"]["properties"]
    if "portfolioId" not in peer_teaser_props:
        fail("HomeResponse.peerTeaser missing portfolioId")


def section_between(text: str, start_marker: str, end_marker: str) -> str:
    start = text.find(start_marker)
    if start == -1:
        fail(f"Missing OpenAPI section: {start_marker.strip()}")
    end = text.find(end_marker, start + len(start_marker))
    if end == -1:
        fail(f"Missing OpenAPI section end: {end_marker.strip()}")
    return text[start:end]


def assert_openapi_text_contract(path: Path) -> None:
    text = path.read_text(encoding="utf-8")

    if "  version: 1.0.0" not in text:
        fail("OpenAPI info.version must be 1.0.0")
    if "/api/coach/recommendations" in text:
        fail("POST /api/coach/recommendations must not be a public P0 API")

    operation_ids = re.findall(r"^\s+operationId:\s+([A-Za-z0-9_]+)\s*$", text, re.MULTILINE)
    if len(operation_ids) != 12:
        fail(f"Expected 12 public P0 operations, found {len(operation_ids)}")
    if len(set(operation_ids)) != 12:
        fail("Every operation must have a unique operationId")

    mission_request = section_between(text, "    MissionRequest:", "    MissionResponse:")
    for field in ["simulationId:", "missionTemplateId:", "triggerSource:", "recommendationSource:", "difficulty:"]:
        if field not in mission_request:
            fail(f"MissionRequest missing {field.rstrip(':')}")
    if re.search(r"^\s+source:\s*$", mission_request, re.MULTILINE):
        fail("MissionRequest must not contain source")

    privacy_patch = section_between(text, "    PrivacySettingsPatchRequest:", "    PrivacyWithdrawRequest:")
    if "minProperties: 1" not in privacy_patch:
        fail("PrivacySettingsPatchRequest must have minProperties: 1")

    privacy_withdraw = section_between(text, "    PrivacyWithdrawRequest:", "    AnonymousPortfolioWithdrawRequest:")
    if "oneOf:" not in privacy_withdraw or privacy_withdraw.count("- $ref:") != 2:
        fail("PrivacyWithdrawRequest must use oneOf with two request shapes")

    required_schema_fields = {
        schema_name: [f"{field}:" for field in fields]
        for schema_name, fields in EXPECTED_RESPONSE_FIELDS.items()
    }
    required_schema_fields["HomeResponse"].append("portfolioId:")
    for schema_name, fields in required_schema_fields.items():
        next_marker = {
            "HomeResponse": "    PortfolioResponse:",
            "PortfolioResponse": "    FinancialSummary:",
            "ComparisonResponse": "    SimulationRequest:",
            "SimulationResponse": "    MissionRequest:",
            "MissionResponse": "    PrivacySettingsResponse:",
            "PrivacySettingsResponse": "    PrivacySettingsPatchRequest:",
        }[schema_name]
        section = section_between(text, f"    {schema_name}:", next_marker)
        for field in fields:
            if field not in section:
                fail(f"{schema_name} missing {field.rstrip(':')}")


def assert_seed_contract() -> None:
    required_files = [
        "users.json",
        "onboarding-diagnoses.json",
        "onboarding-sessions.json",
        "mydata-connections.json",
        "privacy-settings.json",
        "personas.json",
        "feature-vectors.json",
        "portfolios.json",
        "mission-templates.json",
    ]

    data = {}
    for filename in required_files:
        path = SEED_DIR / filename
        if not path.exists():
            fail(f"Missing seed file: {filename}")
        data[filename] = load_json(path)

    users = {item["id"]: item for item in data["users.json"]}
    personas = {item["id"]: item for item in data["personas.json"]}
    diagnoses = {item["id"]: item for item in data["onboarding-diagnoses.json"]}
    sessions = {item["id"]: item for item in data["onboarding-sessions.json"]}
    connections = {item["id"]: item for item in data["mydata-connections.json"]}
    settings = {item["id"]: item for item in data["privacy-settings.json"]}
    portfolios = {item["id"]: item for item in data["portfolios.json"]}
    templates = {item["id"]: item for item in data["mission-templates.json"]}

    for fixed_id, collection in [
        ("demo-user-001", users),
        ("P001", personas),
        ("diag-001", diagnoses),
        ("mydata-mock-001", connections),
        ("privacy-001", settings),
        ("peer-portfolio-023", portfolios),
        ("own-portfolio-001", portfolios),
        ("MISSION_AUTO_TRANSFER_SMALL", templates),
    ]:
        if fixed_id not in collection:
            fail(f"Missing fixed seed id: {fixed_id}")

    if not any(session.get("onboardingToken") == "onb-token-001" for session in sessions.values()):
        fail("Missing onboardingToken onb-token-001")

    if not any(session.get("expectedAccessToken") == "demo-token" for session in sessions.values()):
        fail("Missing demo-token reference")

    mission_title = templates["MISSION_AUTO_TRANSFER_SMALL"].get("titleTemplate")
    if mission_title != "이번 달 비상금 자동이체 10만 원 설정하기":
        fail("Mission title must be monthly 100,000 KRW wording")

    if portfolios["peer-portfolio-023"]["status"] != "ACTIVE":
        fail("peer-portfolio-023 must start ACTIVE")
    if portfolios["own-portfolio-001"]["status"] != "ACTIVE":
        fail("own-portfolio-001 must start ACTIVE")

    if settings["privacy-001"]["preview"]["portfolioId"] != "own-portfolio-001":
        fail("Privacy preview must point to own-portfolio-001")
    if settings["privacy-001"].get("ownPortfolioId") != "own-portfolio-001":
        fail("Privacy settings ownPortfolioId must point to own-portfolio-001")
    if settings["privacy-001"].get("friendShareDefault") != "NONE":
        fail("Default friendShareDefault must be NONE")

    demo_feature = next(item for item in data["feature-vectors.json"] if item["userId"] == "demo-user-001")
    before_ratio = demo_feature["cashLikeAssets"] / demo_feature["monthlyEssentialSpending"]
    if demo_feature["cashLikeAssets"] != 400000:
        fail("demo cashLikeAssets must be 400000")
    if demo_feature["monthlyEssentialSpending"] != 1000000:
        fail("demo monthlyEssentialSpending must be 1000000")
    if round(before_ratio, 1) != demo_feature["emergencyFundMonths"]:
        fail("demo emergencyFundMonths must equal cashLikeAssets / monthlyEssentialSpending")
    after_cash = demo_feature["cashLikeAssets"] + 100000 * 3
    after_ratio = after_cash / demo_feature["monthlyEssentialSpending"]
    if after_cash != 700000 or round(after_ratio, 1) != 0.7:
        fail("simulation math must produce 700000 cashLikeAssets and 0.7 emergencyFundMonths")

    for portfolio_id in ["peer-portfolio-023", "own-portfolio-001"]:
        portfolio = portfolios[portfolio_id]
        if "dataMode" not in portfolio:
            fail(f"{portfolio_id} missing dataMode")
        if "privacyBadges" not in portfolio:
            fail(f"{portfolio_id} missing privacyBadges")


def assert_request_contract() -> None:
    text = REQUEST_PATH.read_text(encoding="utf-8")

    required_snippets = [
        "POST {{baseUrl}}/api/onboarding/diagnosis",
        "POST {{baseUrl}}/api/mydata/mock-consent",
        "POST {{baseUrl}}/api/privacy/consents",
        "POST {{baseUrl}}/api/demo/session",
        "GET {{baseUrl}}/api/home",
        "GET {{baseUrl}}/api/explore/portfolios/{{peerPortfolioId}}",
        "POST {{baseUrl}}/api/comparisons",
        "POST {{baseUrl}}/api/simulations",
        "POST {{baseUrl}}/api/missions",
        "GET {{baseUrl}}/api/privacy/settings",
        "PATCH {{baseUrl}}/api/privacy/settings",
        "POST {{baseUrl}}/api/privacy/withdraw",
        "GET {{baseUrl}}/api/explore/portfolios/{{ownPortfolioId}}",
    ]
    for snippet in required_snippets:
        if snippet not in text:
            fail(f"Request template missing: {snippet}")

    source_example = '"source"' + ': "SIMULATION"'
    if source_example in text:
        fail("Request template must not use source for mission request")

    if '"portfolioId": "peer-portfolio-023"' in text:
        fail("Privacy withdraw request must not target peer-portfolio-023")

    if '"portfolioId": "own-portfolio-001"' not in text:
        fail("Privacy withdraw request must target own-portfolio-001")

    if "Expected: 410" not in text:
        fail("Request template must verify own portfolio 410 after withdraw")
    if '"friendShareDefault": true' in text or '"friendShareDefault": false' in text:
        fail("Request template must use friendShareDefault enum values")
    if "after.cashLikeAssets=700000" not in text:
        fail("Request template must document after.cashLikeAssets=700000")


def assert_evidence_placeholders() -> None:
    evidence_path = ROOT / "docs" / "10_evidence_collection_plan.md"
    presentation_path = ROOT / "docs" / "01_presentation_plan_v1.0.md"
    for path in [evidence_path, presentation_path]:
        text = path.read_text(encoding="utf-8")
        if "[추가 필요]" not in text:
            fail(f"{path.relative_to(ROOT)} must keep evidence placeholders")
    forbidden_evidence_claims = [
        "설문 결과 70%",
        "인터뷰 10명",
        "시장 규모",
        "응답자 중",
    ]
    combined = evidence_path.read_text(encoding="utf-8") + presentation_path.read_text(encoding="utf-8")
    for phrase in forbidden_evidence_claims:
        if phrase in combined:
            fail(f"Evidence docs contain an unsupported claim: {phrase}")


def assert_text_regressions() -> None:
    forbidden = [
        "이번 주 자동이체 " + "3만 원",
        '"source"' + ': "SIMULATION"',
        "v1." + "4.2",
        "1." + "4.2",
        "v1." + "4.1",
        "1." + "4.1",
    ]

    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix not in {".md", ".yaml", ".http", ".json", ".py"}:
            continue
        text = path.read_text(encoding="utf-8")
        for phrase in forbidden:
            if phrase in text:
                fail(f"Forbidden phrase found in {path.relative_to(ROOT)}: {phrase}")


def main() -> int:
    openapi = load_yaml(OPENAPI_PATH)
    if openapi:
        assert_openapi_contract(openapi)
    else:
        assert_openapi_text_contract(OPENAPI_PATH)
    assert_seed_contract()
    assert_request_contract()
    assert_evidence_placeholders()
    assert_text_regressions()
    print("FinMate P0 contract validation passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FinMate P0 contract validation failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
