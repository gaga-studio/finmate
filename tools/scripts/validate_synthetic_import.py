#!/usr/bin/env python3
import json
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    raise AssertionError(message)


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def main() -> None:
    manifest_path = ROOT / "fixtures/dataset-manifests/financial-sns-mydata-202606.json"
    if not manifest_path.exists():
        fail("Missing synthetic dataset source manifest")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("expectedPersonaCount") != 199:
        fail("Synthetic dataset manifest must pin 199 expected personas")
    if manifest.get("policy", {}).get("storeFullDatasetInFinMateRepo") is not False:
        fail("Synthetic dataset manifest must keep the full dataset outside this repo")

    controller = read("apps/api/src/main/java/com/gagastudio/finmate/api/controller/FinmateController.java")
    dtos = read("apps/api/src/main/java/com/gagastudio/finmate/api/dto/ApiDtos.java")
    service = read("apps/api/src/main/java/com/gagastudio/finmate/api/product/SyntheticDatasetImportService.java")
    importer = read("tools/scripts/import-synthetic-mydata.py")
    readme = read("README.md")

    for snippet in [
        "PostMapping(\"/api/dev/import-synthetic-dataset\")",
        "devToolsEnabled",
        "SyntheticDatasetImportService",
    ]:
        if snippet not in controller:
            fail(f"Controller missing synthetic import snippet: {snippet}")

    for snippet in [
        "DevSyntheticImportRequest",
        "DevSyntheticImportResponse",
        "DevSyntheticUserPayload",
        "DevSyntheticSnapshotPayload",
        "DevSyntheticBirthdayFundPayload",
    ]:
        if snippet not in dtos:
            fail(f"DTO missing synthetic import record: {snippet}")

    for snippet in [
        "resetSyntheticUsers",
        "synthetic-",
        "financial_snapshots",
        "daily_records",
        "friendships",
        "birthday_funds",
        "SYNTHETIC_MYDATA",
    ]:
        if snippet not in service:
            fail(f"Import service missing persistence snippet: {snippet}")

    for snippet in [
        "--dry-run",
        "--reset-synthetic",
        "--persona",
        "@synthetic.finmate.local",
        "/api/dev/import-synthetic-dataset",
    ]:
        if snippet not in importer:
            fail(f"Importer script missing CLI/API snippet: {snippet}")

    for snippet in [
        "import-synthetic-mydata.py",
        "p001@synthetic.finmate.local",
        "financial-sns-mydata-202606",
    ]:
        if snippet not in readme:
            fail(f"README missing synthetic import guide: {snippet}")

    dry_run_summary = run_importer_dry_run()
    if dry_run_summary["status"] != "DRY_RUN_OK":
        fail("Importer dry-run did not return DRY_RUN_OK")
    if dry_run_summary["selectedUsers"] != 2:
        fail("Importer dry-run should select 2 mini fixture users")
    if dry_run_summary["missions"] < 2 or dry_run_summary["dailyRecords"] != 2:
        fail("Importer dry-run did not synthesize required missions/daily records")

    print("FinMate synthetic MyData import validation passed")


def run_importer_dry_run() -> dict:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        aggregates = root / "aggregates"
        validation = root / "validation"
        aggregates.mkdir()
        validation.mkdir()
        (aggregates / "personas.jsonl").write_text(
            "\n".join([
                json.dumps({
                    "persona_id": "P001",
                    "synthetic_name": "가상청년 P001",
                    "age": 24,
                    "archetype": "학생/알바",
                    "job": "휴학생/단기알바",
                    "region": "서울 성동구",
                    "income_band": "150~250만원",
                    "risk_attitude": "안정추구형",
                    "household_type": "1인가구",
                    "financial_goal": "비상금",
                    "lifestyle_tags": ["가성비", "예산관리"],
                    "monthly_income_krw": 1700000,
                    "target_monthly_spend_krw": 1170000,
                }, ensure_ascii=False),
                json.dumps({
                    "persona_id": "P002",
                    "synthetic_name": "가상청년 P002",
                    "age": 29,
                    "archetype": "사회초년생",
                    "job": "IT/개발",
                    "region": "서울 강남권",
                    "income_band": "250~350만원",
                    "risk_attitude": "중립형",
                    "household_type": "1인가구",
                    "financial_goal": "내집마련",
                    "lifestyle_tags": ["저축", "투자"],
                    "monthly_income_krw": 3200000,
                    "target_monthly_spend_krw": 1800000,
                }, ensure_ascii=False),
            ]),
            encoding="utf-8",
        )
        (aggregates / "feature_matrix.csv").write_text(
            "\n".join([
                "persona_id,archetype,age,monthly_income_krw,total_income_krw,total_spend_krw,total_saving_krw,total_invest_buy_krw,savings_rate,investment_rate,spend_rate,food_ratio,cafe_ratio,transport_spend_krw,online_spend_ratio,weekend_spend_ratio,subscription_count,risk_score,household_size,transaction_count,portfolio_count,social_post_count,cluster_id",
                "P001,학생/알바,24,1700000,1700000,1200000,150000,76000,0.088,0.045,0.70,0.10,0.02,120000,0.1,0.2,3,2,1,10,1,3,1",
                "P002,사회초년생,29,3200000,3200000,1800000,600000,190000,0.18,0.06,0.56,0.12,0.03,180000,0.2,0.1,5,3,1,12,2,3,2",
            ]),
            encoding="utf-8",
        )
        (aggregates / "ledger_all.csv").write_text(
            "\n".join([
                "날짜,시간,타입,대분류,소분류,내용,금액,화폐,결제수단,메모,persona_id,transaction_id,cashflow_bucket,account_ref,api_ref",
                "2026-06-12,12:00,지출,식비,한식,점심,-7800,KRW,카드,,P001,P001-T001,소비,P001-CARD,card/demo.json#1",
                "2026-06-12,08:00,지출,교통,대중교통,지하철,-1450,KRW,카드,,P001,P001-T002,소비,P001-CARD,card/demo.json#2",
                "2026-06-12,12:00,지출,카페/간식,커피,커피,-4500,KRW,카드,,P002,P002-T001,소비,P002-CARD,card/demo.json#1",
                "2026-06-12,19:00,지출,식비,양식,저녁,-12000,KRW,카드,,P002,P002-T002,소비,P002-CARD,card/demo.json#2",
            ]),
            encoding="utf-8",
        )
        (aggregates / "social_edges.csv").write_text("source_persona_id,target_persona_id,edge_type\nP001,P002,follow\nP002,P001,follow\n", encoding="utf-8")
        (aggregates / "social_feed.csv").write_text(
            "\n".join([
                "post_id,persona_id,handle,created_at,post_type,visibility,source,title,body,metrics",
                "P001-POST-001,P001,fin_p001,2026-06-12T10:00:00+09:00,saving_milestone,public,derived,저축률 기록,저축률 8% 달성,{}",
                "P002-POST-001,P002,fin_p002,2026-06-12T10:00:00+09:00,investment_trade,public,derived,투자 기록,투자 매수 190000원,{}",
            ]),
            encoding="utf-8",
        )
        (aggregates / "social_reactions.csv").write_text("reaction_id,actor_persona_id,target_persona_id,reaction_type,created_at\n", encoding="utf-8")
        (validation / "validation_report.json").write_text(json.dumps({"status": "PASS"}), encoding="utf-8")
        result = subprocess.run(
            [
                "python3",
                str(ROOT / "tools/scripts/import-synthetic-mydata.py"),
                "--source",
                str(root),
                "--limit",
                "2",
                "--dry-run",
            ],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
        return json.loads(result.stdout)


if __name__ == "__main__":
    main()
