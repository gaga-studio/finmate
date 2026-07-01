#!/usr/bin/env python3
"""Import the external financial SNS MyData dataset into FinMate dev Postgres via the API."""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DATASET_REPO = "https://github.com/gaga-studio/financial-sns-mydata-202606"
DEFAULT_DATASET_ROOT = "outputs/financial_sns_mydata_202606"
DEFAULT_PASSWORD = "password123!"
DEFAULT_MONTH = "2026-06"
TARGET_RECORD_DATE = "2026-06-12"
SYNTHETIC_LABEL_RE = re.compile(r"가상청년 (P\d{3})")
DISPLAY_NAME_POOL = [
    "하민", "지우", "서연", "도윤", "하린", "유준", "나윤", "시우", "예린", "준서",
    "수아", "현우", "채원", "지호", "다은", "건우", "유나", "태윤", "소율", "은우",
    "하준", "서아", "이안", "윤서", "지민", "서준", "하율", "유진", "아린", "도현",
    "채윤", "민서", "주원", "서진", "예준", "다온", "연우", "지안", "하람", "시윤",
    "가온", "재윤", "유하", "선우", "은서", "도하", "라온", "예나", "지율", "태오",
]


def main() -> None:
    args = parse_args()
    manifest = read_manifest()
    source = resolve_source(args.source)
    personas = read_personas(source / "aggregates/personas.jsonl")
    features = read_csv_by_key(source / "aggregates/feature_matrix.csv", "persona_id")
    ledger_rows = read_csv(source / "aggregates/ledger_all.csv")
    social_edges = read_edges(source / "aggregates/social_edges.csv")
    social_feed = read_feed(source / "aggregates/social_feed.csv")

    selected_ids = select_personas(personas, args.persona, args.limit)
    validate_source(source, selected_ids, args.require_bundles)
    payload = build_payload(
        selected_ids=selected_ids,
        personas=personas,
        features=features,
        ledger_rows=ledger_rows,
        social_edges=social_edges,
        social_feed=social_feed,
        password=args.password,
        reset_synthetic=args.reset_synthetic,
        manifest=manifest,
        source_commit=args.source_commit or manifest.get("sourceCommit", "unknown"),
    )

    summary = {
        "source": str(source),
        "selectedUsers": len(payload["users"]),
        "snapshots": len(payload["users"]),
        "dailyRecords": sum(len(user["dailyRecords"]) for user in payload["users"]),
        "missions": sum(len(user["missions"]) for user in payload["users"]),
        "friendships": sum(len(user.get("follows") or []) for user in payload["users"]),
        "feedItems": sum(len(user.get("feedItems") or []) for user in payload["users"]),
        "birthdayFunds": sum(1 for user in payload["users"] if user.get("birthdayFund")),
    }

    if args.dry_run:
        print(json.dumps({"status": "DRY_RUN_OK", **summary}, ensure_ascii=False, indent=2))
        return

    response = post_payload(args.api_url.rstrip("/") + "/api/dev/import-synthetic-dataset", payload)
    print(json.dumps({"request": summary, "response": response}, ensure_ascii=False, indent=2))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Import FinMate synthetic MyData users from the external dataset.")
    parser.add_argument("--source", default=os.environ.get("FINMATE_SYNTHETIC_DATASET_DIR"), help="Dataset root or repository checkout path.")
    parser.add_argument("--api-url", default=os.environ.get("FINMATE_API_URL", "http://localhost:8080"), help="FinMate API base URL.")
    parser.add_argument("--limit", type=int, default=199, help="Maximum personas to import. Defaults to 199.")
    parser.add_argument("--persona", action="append", default=[], help="Import one specific persona id. Can be repeated.")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="Password for generated synthetic login accounts.")
    parser.add_argument("--source-commit", default=None, help="Override source commit recorded in the import payload.")
    parser.add_argument("--reset-synthetic", action="store_true", help="Delete previously imported synthetic-* users before import.")
    parser.add_argument("--require-bundles", action="store_true", help="Fail when selected bundle directories or ledger/profile files are missing.")
    parser.add_argument("--dry-run", action="store_true", help="Validate and print import summary without calling the API.")
    return parser.parse_args()


def read_manifest() -> dict[str, Any]:
    path = ROOT / "fixtures/dataset-manifests/financial-sns-mydata-202606.json"
    if not path.exists():
        return {
            "sourceRepository": DEFAULT_DATASET_REPO,
            "datasetRoot": DEFAULT_DATASET_ROOT,
            "sourceCommit": "unknown",
            "importVersion": "2026-06",
        }
    return json.loads(path.read_text(encoding="utf-8"))


def resolve_source(source_arg: str | None) -> Path:
    if not source_arg:
        raise SystemExit("Missing --source or FINMATE_SYNTHETIC_DATASET_DIR.")
    source = Path(source_arg).expanduser().resolve()
    if (source / DEFAULT_DATASET_ROOT).exists():
        source = source / DEFAULT_DATASET_ROOT
    if not (source / "aggregates").exists():
        raise SystemExit(f"Dataset aggregates directory not found: {source / 'aggregates'}")
    return source


def read_personas(path: Path) -> dict[str, dict[str, Any]]:
    if not path.exists():
        raise SystemExit(f"Missing personas.jsonl: {path}")
    personas: dict[str, dict[str, Any]] = {}
    with path.open("r", encoding="utf-8-sig") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            personas[row["persona_id"]] = row
    return personas


def read_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        raise SystemExit(f"Missing required CSV: {path}")
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def read_csv_by_key(path: Path, key: str) -> dict[str, dict[str, str]]:
    return {row[key]: row for row in read_csv(path)}


def read_edges(path: Path) -> dict[str, list[str]]:
    edges: dict[str, list[str]] = defaultdict(list)
    for row in read_csv(path):
        if row.get("edge_type") == "follow":
            edges[row["source_persona_id"]].append(row["target_persona_id"])
    return edges


def read_feed(path: Path) -> dict[str, list[dict[str, str]]]:
    feed: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in read_csv(path):
        feed[row["persona_id"]].append(row)
    return feed


def select_personas(personas: dict[str, dict[str, Any]], requested: list[str], limit: int) -> list[str]:
    if requested:
        missing = sorted(set(requested) - set(personas))
        if missing:
            raise SystemExit(f"Unknown persona ids: {missing}")
        return sorted(dict.fromkeys(requested))
    return sorted(personas)[:limit]


def validate_source(source: Path, selected_ids: list[str], require_bundles: bool) -> None:
    required = [
        source / "aggregates/personas.jsonl",
        source / "aggregates/feature_matrix.csv",
        source / "aggregates/ledger_all.csv",
        source / "aggregates/social_edges.csv",
        source / "aggregates/social_feed.csv",
        source / "validation/validation_report.json",
    ]
    missing = [str(path) for path in required if not path.exists()]
    if missing:
        raise SystemExit("Missing dataset files:\n" + "\n".join(missing))
    if require_bundles:
        missing_bundle_files: list[str] = []
        for persona_id in selected_ids:
            bundle = source / "bundles" / persona_id
            for relative in ["profile.json", "ledger.csv", "social/feed.json", "social/follows.json"]:
                path = bundle / relative
                if not path.exists():
                    missing_bundle_files.append(str(path))
        if missing_bundle_files:
            preview = "\n".join(missing_bundle_files[:20])
            suffix = "" if len(missing_bundle_files) <= 20 else f"\n... {len(missing_bundle_files) - 20} more"
            raise SystemExit("Missing selected bundle files:\n" + preview + suffix)


def build_payload(
    *,
    selected_ids: list[str],
    personas: dict[str, dict[str, Any]],
    features: dict[str, dict[str, str]],
    ledger_rows: list[dict[str, str]],
    social_edges: dict[str, list[str]],
    social_feed: dict[str, list[dict[str, str]]],
    password: str,
    reset_synthetic: bool,
    manifest: dict[str, Any],
    source_commit: str,
) -> dict[str, Any]:
    ledger_by_persona: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in ledger_rows:
        if row.get("persona_id") in selected_ids:
            ledger_by_persona[row["persona_id"]].append(row)

    selected_set = set(selected_ids)
    birthday_viewer = selected_ids[0] if selected_ids else None
    birthday_owner = selected_ids[1] if len(selected_ids) > 1 else birthday_viewer
    display_names = {persona_id: display_name_for(persona_id, personas[persona_id]) for persona_id in selected_ids}
    users = []
    for persona_id in selected_ids:
        persona = personas[persona_id]
        feature = features.get(persona_id)
        if not feature:
            raise SystemExit(f"Missing feature row for {persona_id}")
        ledger = ledger_by_persona.get(persona_id, [])
        follows = [target for target in social_edges.get(persona_id, []) if target in selected_set][:5]
        feed_items = build_feed_items(persona_id, follows, social_feed, display_names)
        birthday_fund = None
        if persona_id == birthday_viewer and birthday_owner:
            owner_name = display_names[birthday_owner]
            feed_items.insert(0, {
                "feedId": f"feed-{persona_id}-birthday",
                "actorPersonaId": birthday_owner,
                "kind": "BIRTHDAY",
                "title": f"{owner_name}님의 생일 펀드가 열렸어요",
                "body": "친구들이 함께 모으는 생일 축하 펀드",
                "amount": 72000,
            })
            birthday_fund = {
                "fundId": "fund-jiwoo",
                "ownerPersonaId": birthday_owner,
                "title": f"{owner_name}님의 생일 펀드",
                "targetAmount": 100000,
                "currentAmount": 72000,
                "dueDate": "2026-06-15",
                "status": "OPEN",
                "shareCode": "SYNTH-BDAY-2026",
            }
        users.append({
            "personaId": persona_id,
            "email": f"{persona_id.lower()}@synthetic.finmate.local",
            "password": password,
            "displayName": display_names[persona_id],
            "profile": build_profile(persona),
            "privacy": {
                "anonymousPortfolioOptIn": True,
                "friendShareDefault": "MISSION_ONLY",
                "exposedFields": ["ageBand", "goalType", "financialSummary", "missionStatus"],
            },
            "mydata": {
                "consentVersion": "synthetic-mydata-v1.6",
                "scopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"],
            },
            "wallet": build_wallet(persona_id),
            "snapshot": build_snapshot(persona, feature, ledger),
            "dailyRecords": [build_daily_record(feature, ledger)],
            "missions": build_missions(feature),
            "follows": follows,
            "feedItems": feed_items[:8],
            "birthdayFund": birthday_fund,
        })
    return {
        "importVersion": manifest.get("importVersion", "2026-06"),
        "sourceRepository": manifest.get("sourceRepository", DEFAULT_DATASET_REPO),
        "sourceCommit": source_commit,
        "resetSynthetic": reset_synthetic,
        "users": users,
    }


def display_name_for(persona_id: str, persona: dict[str, Any]) -> str:
    source_name = str(persona.get("synthetic_name") or "").strip()
    if source_name and not SYNTHETIC_LABEL_RE.fullmatch(source_name):
        return source_name
    try:
        number = int(persona_id[1:])
    except ValueError:
        number = 1
    return DISPLAY_NAME_POOL[(number - 1) % len(DISPLAY_NAME_POOL)]


def replace_synthetic_labels(text: str, display_names: dict[str, str]) -> str:
    return SYNTHETIC_LABEL_RE.sub(lambda match: display_names.get(match.group(1), match.group(0)), text)


def build_profile(persona: dict[str, Any]) -> dict[str, str]:
    return {
        "ageBand": age_band(int(persona["age"])),
        "incomeBand": str(persona.get("income_band") or ""),
        "jobCategory": str(persona.get("job") or persona.get("archetype") or "청년"),
        "householdType": str(persona.get("household_type") or "1인가구"),
        "moneyStyle": str(persona.get("risk_attitude") or "안정 추구형"),
        "area": str(persona.get("region") or "서울"),
        "goalType": goal_type(str(persona.get("financial_goal") or "")),
        "painPoint": pain_point(str(persona.get("archetype") or ""), persona.get("lifestyle_tags") or []),
    }


def build_wallet(persona_id: str) -> dict[str, int]:
    number = int(persona_id[1:])
    return {
        "pointBalance": 500 + (number * 37) % 2500,
        "virtualMoneyBalance": 100000,
    }


def build_snapshot(persona: dict[str, Any], feature: dict[str, str], ledger: list[dict[str, str]]) -> dict[str, Any]:
    monthly_income = int(float(feature.get("total_income_krw") or persona.get("monthly_income_krw") or 0))
    monthly_spending = int(float(feature.get("total_spend_krw") or persona.get("target_monthly_spend_krw") or 0))
    monthly_saving = int(float(feature.get("total_saving_krw") or 0))
    investment_value = int(float(feature.get("total_invest_buy_krw") or 0))
    cash_like_assets = max(monthly_saving + int(monthly_income * 0.12), int(monthly_income * 0.18), 100000)
    emergency_months = round(cash_like_assets / max(monthly_spending, 1), 2)
    categories = aggregate_categories(ledger, month_level=True)
    if not categories:
        categories = estimate_categories_from_features(feature, monthly_spending)
    return {
        "month": DEFAULT_MONTH,
        "monthlyIncome": monthly_income,
        "monthlySpending": monthly_spending,
        "monthlySaving": monthly_saving,
        "investmentValue": investment_value,
        "cashLikeAssets": cash_like_assets,
        "emergencyFundMonths": emergency_months,
        "spendingCategories": categories,
        "lifestyleTags": list(persona.get("lifestyle_tags") or [])[:4] or ["합성데이터"],
    }


def build_daily_record(feature: dict[str, str], ledger: list[dict[str, str]]) -> dict[str, Any]:
    day_rows = [row for row in ledger if row.get("날짜") == TARGET_RECORD_DATE]
    categories = aggregate_categories(day_rows, month_level=False)
    spent = sum(categories.values())
    monthly_spending = int(float(feature.get("total_spend_krw") or 0))
    if spent == 0:
        budget = max(10000, round_to_thousand(monthly_spending // 30))
        categories = estimate_categories_from_features(feature, int(budget * 0.78))
        spent = sum(categories.values())
    else:
        budget = max(10000, round_to_thousand(int(spent * 1.25)))
    return {
        "recordDate": TARGET_RECORD_DATE,
        "budget": budget,
        "spent": spent,
        "categorySpending": categories,
        "missionStatus": "IN_PROGRESS",
        "pointDelta": 0,
    }


def build_missions(feature: dict[str, str]) -> list[dict[str, Any]]:
    food_ratio = float(feature.get("food_ratio") or 0)
    cafe_ratio = float(feature.get("cafe_ratio") or 0)
    savings_rate = float(feature.get("savings_rate") or 0)
    investment_rate = float(feature.get("investment_rate") or 0)
    subscription_count = int(float(feature.get("subscription_count") or 0))
    missions = [{
        "missionId": "mission-food",
        "title": "내일 식비 10,000원 이하 사용하기",
        "description": "하루 식비를 낮춰 남는 금액을 비상금으로 옮겨요.",
        "status": "ACTIVE",
        "difficulty": "EASY",
        "rewardPoints": 120,
        "progress": 78 if food_ratio < 0.12 else 42,
        "source": "SYNTHETIC_MYDATA_IMPORT",
    }]
    missions.append({
        "missionId": "mission-saving",
        "title": "저축하기 습관 만들기",
        "description": "이번 주 남는 금액을 비상금으로 따로 모아봐요.",
        "status": "ACTIVE",
        "difficulty": "EASY" if savings_rate >= 0.15 else "NORMAL",
        "rewardPoints": 200,
        "progress": max(20, min(85, int(savings_rate * 180))),
        "source": "SYNTHETIC_MYDATA_IMPORT",
    })
    if subscription_count > 0 or cafe_ratio > 0.02:
        missions.append({
            "missionId": "mission-fixed-cost",
            "title": "고정 지출 5% 줄이기",
            "description": "구독과 반복 결제를 점검해 다음 달 현금흐름을 가볍게 만들어요.",
            "status": "ACTIVE",
            "difficulty": "NORMAL",
            "rewardPoints": 180,
            "progress": 45,
            "source": "SYNTHETIC_MYDATA_IMPORT",
        })
    if investment_rate > 0.04:
        missions.append({
            "missionId": "mission-invest",
            "title": "투자 비중 점검하기",
            "description": "이번 달 매수 금액과 현금 비중을 함께 확인해요.",
            "status": "ACTIVE",
            "difficulty": "NORMAL",
            "rewardPoints": 150,
            "progress": max(15, min(80, int(investment_rate * 250))),
            "source": "SYNTHETIC_MYDATA_IMPORT",
        })
    return missions[:4]


def build_feed_items(
    persona_id: str,
    follows: list[str],
    social_feed: dict[str, list[dict[str, str]]],
    display_names: dict[str, str],
) -> list[dict[str, Any]]:
    feed_items: list[dict[str, Any]] = []
    targets = follows or [persona_id]
    for target in targets[:4]:
        for post in social_feed.get(target, [])[:2]:
            title = replace_synthetic_labels(post.get("title") or "금융 루틴 업데이트", display_names)
            body = replace_synthetic_labels(post.get("body") or "합성 금융 활동에서 파생된 피드입니다.", display_names)
            feed_items.append({
                "feedId": f"feed-{persona_id}-{post['post_id']}",
                "actorPersonaId": target,
                "kind": feed_kind(post.get("post_type", "")),
                "title": title,
                "body": body,
                "amount": extract_amount(body),
            })
    return feed_items


def aggregate_categories(rows: list[dict[str, str]], month_level: bool) -> dict[str, int]:
    categories: dict[str, int] = defaultdict(int)
    for row in rows:
        if row.get("타입") != "지출" and row.get("cashflow_bucket") != "소비":
            continue
        key = app_category(row.get("대분류") or "")
        try:
            amount = abs(int(float(row.get("금액") or 0)))
        except ValueError:
            amount = 0
        categories[key] += amount
    ordered = ["식비", "교통비", "카페/간식", "기타"]
    if month_level:
        return {key: categories[key] for key in ordered if categories[key] > 0}
    return {key: categories[key] for key in ordered}


def estimate_categories_from_features(feature: dict[str, str], total: int) -> dict[str, int]:
    total = max(total, 0)
    food = int(total * float(feature.get("food_ratio") or 0.35))
    cafe = int(total * float(feature.get("cafe_ratio") or 0.08))
    transport = min(int(float(feature.get("transport_spend_krw") or total * 0.12) / 30), max(total - food - cafe, 0))
    other = max(total - food - cafe - transport, 0)
    return {"식비": food, "교통비": transport, "카페/간식": cafe, "기타": other}


def app_category(raw: str) -> str:
    if raw == "식비":
        return "식비"
    if raw == "교통":
        return "교통비"
    if raw == "카페/간식":
        return "카페/간식"
    return "기타"


def age_band(age: int) -> str:
    if age < 25:
        return "20대 초반"
    if age < 30:
        return "20대 후반"
    return "30대 초반"


def goal_type(goal: str) -> str:
    if "비상" in goal:
        return "EMERGENCY_FUND"
    if "내집" in goal or "독립" in goal or "청약" in goal:
        return "HOUSING"
    if "여행" in goal:
        return "TRAVEL"
    if "투자" in goal:
        return "INVESTING"
    return "SAVING"


def pain_point(archetype: str, tags: list[str]) -> str:
    text = " ".join([archetype, *map(str, tags)])
    if "구독" in text or "소비" in text:
        return "REDUCE_SPENDING"
    if "투자" in text:
        return "START_INVESTING"
    if "절약" in text or "저축" in text:
        return "SAVE_CONSISTENTLY"
    return "BUILD_ROUTINE"


def feed_kind(post_type: str) -> str:
    if "saving" in post_type:
        return "SAVING"
    if "investment" in post_type:
        return "INVESTMENT"
    if "spend" in post_type:
        return "SPENDING"
    return "MISSION"


def extract_amount(text: str) -> int | None:
    digits = "".join(ch for ch in text if ch.isdigit())
    if not digits:
        return None
    value = int(digits)
    if value <= 100:
        return None
    return value


def round_to_thousand(value: int) -> int:
    return max(0, int(round(value / 1000.0) * 1000))


def post_payload(url: str, payload: dict[str, Any]) -> dict[str, Any]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=body, method="POST", headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        error_body = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"Import API failed with HTTP {error.code}: {error_body}") from error
    except urllib.error.URLError as error:
        raise SystemExit(f"Import API is not reachable at {url}: {error}") from error


if __name__ == "__main__":
    main()
