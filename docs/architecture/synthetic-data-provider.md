# 합성 MyData Provider 구조

FinMate의 제품형 MVP는 런타임 상태를 Postgres에 저장합니다. 개발/QA용 금융 데이터는 `gaga-studio/financial-sns-mydata-202606` 레포에서 가져오되, 원본 전체 데이터셋은 FinMate 레포에 복사하지 않습니다.

## 왜 importer 방식인가

- 데이터셋 레포는 금융 활동 원장, MyData JSON, SNS companion 데이터를 함께 가진 별도 산출물입니다.
- FinMate 레포는 앱 코드, 계약, DB schema, importer만 관리합니다.
- submodule로 묶지 않기 때문에 앱 개발자는 평소에 큰 데이터셋 checkout 없이 작업할 수 있습니다.
- 필요할 때만 외부 dataset checkout 경로를 지정해 Postgres에 synthetic user state를 적재합니다.

## Source Manifest

기준 source는 `fixtures/dataset-manifests/financial-sns-mydata-202606.json`에 고정합니다.

- repository: `https://github.com/gaga-studio/financial-sns-mydata-202606`
- dataset root: `outputs/financial_sns_mydata_202606`
- expected personas: `199`
- import version: `2026-06`
- source commit: manifest의 `sourceCommit`

## Import Flow

```text
external dataset checkout
-> tools/scripts/import-synthetic-mydata.py
-> POST /api/dev/import-synthetic-dataset
-> SyntheticDatasetImportService
-> Postgres product tables
-> /api/app/home, /compare, /missions, /records, /profile
```

importer는 dataset의 aggregate 파일을 읽어 FinMate DB payload로 변환합니다.

- `personas.jsonl`: 프로필, 직업, 소득대, 가구형태, 목표
- `feature_matrix.csv`: 월 소득, 지출, 저축, 투자, 위험성향 feature
- `ledger_all.csv`: 거래 원장, 지출 카테고리, 일별 기록
- `social_edges.csv`: 팔로우 관계
- `social_feed.csv`: 친구 피드 후보

## Generated Accounts

import 후 synthetic 계정은 다음 규칙으로 생성됩니다.

```text
p001@synthetic.finmate.local / password123!
p002@synthetic.finmate.local / password123!
...
p199@synthetic.finmate.local / password123!
```

사용자 id는 `synthetic-P001` 형식입니다. `--reset-synthetic`은 `synthetic-*` 사용자와 synthetic birthday fund 상태만 지우며, 일반 회원가입 사용자의 DB 상태는 건드리지 않습니다.

## Data Mapping

- `users`, `user_profiles`, `privacy_settings`: persona/profile 기반
- `mydata_connections`, `consent_events`: 합성 MyData 연결 상태
- `financial_snapshots`: 월 소득, 지출, 저축, 투자, 카테고리 집계
- `daily_records`: `2026-06-12` 기준 하루 예산/지출 카드
- `financial_transactions`: 거래 원장 기반 행동 데이터
- `missions`: feature 기반 rule-based mission 후보
- `mission_events`, `point_transactions`: importer가 직접 완료 처리하지 않고, `MissionEvaluationService`가 행동 데이터를 평가해 성공 시 최초 1회만 생성
- `friendships`, `feed_items`: SNS companion aggregate 기반
- `birthday_funds`: 첫 synthetic user에 deterministic 생일펀드 이벤트 부여

## Commands

```bash
FINMATE_SYNTHETIC_DATASET_DIR=../financial-sns-mydata-202606/outputs/financial_sns_mydata_202606 \
  tools/scripts/import-synthetic-mydata.py --dry-run

FINMATE_SYNTHETIC_DATASET_DIR=../financial-sns-mydata-202606/outputs/financial_sns_mydata_202606 \
  tools/scripts/import-synthetic-mydata.py --reset-synthetic

python3 tools/scripts/validate_synthetic_import.py
```

`POST /api/dev/import-synthetic-dataset`는 `FINMATE_DEV_TOOLS_ENABLED=true`일 때만 활성화됩니다.
