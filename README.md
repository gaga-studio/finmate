# FinMate

FinMate는 합성 마이데이터 기반 또래 페르소나와 내 금융 상태를 비교하고, 가장 큰 격차를 3개월 시뮬레이션과 오늘의 미션으로 바꾸는 청년 금융 온보딩 서비스입니다.

핵심 흐름:

```text
30초 진단
-> mock 마이데이터 동의
-> 개인정보 동의
-> 홈 금융 요약
-> 또래 사례
-> 1:1 비교
-> 3개월 시뮬레이션
-> 오늘의 미션
-> 개인정보 공개 미리보기/철회
```

## 왜 필요한가

청년에게 부족한 것은 금융 지식 자체보다 “오늘 움직이게 하는 계기”입니다. FinMate는 SNS식 비교 자극을 소비가 아니라 비상금, 저축 루틴, 작은 금융 행동으로 전환합니다.

발표 데모는 실제 금융정보를 사용하지 않습니다. `MOCK_MYDATA`와 `SYNTHETIC_PERSONA` 기반의 합성/mock 데이터로만 시연합니다.

## P0 데모 범위

현재 공개 기준선은 **v1.0.0**입니다. P0는 7분 최종성과발표회에서 안정적으로 보여줄 핵심 흐름만 포함합니다.

| 포함 | 제외 |
| --- | --- |
| 30초 진단 | 실제 마이데이터 연동 |
| mock 마이데이터 동의 | 실사용 결제/송금 |
| 홈 금융 요약 | 투자상품 추천 |
| 또래 포트폴리오 상세 | LLM 필수 호출 |
| 1:1 비교와 `mainGap` | Birthday Wish Money P0 구현 |
| 3개월 시뮬레이션 | 포인트 원장 |
| 오늘의 미션 생성 | 친구 피드 원천 금융정보 노출 |
| 개인정보 공개 미리보기/철회 | 신규 P0 API 추가 |

## Frozen Contract

- 공개 P0 API는 12개로 고정합니다.
- 로드맵 결정 없이 신규 P0 API를 추가하지 않습니다.
- `POST /api/coach/recommendations`는 공개 P0 API가 아닙니다.
- P0 추천은 rule-based 기본이며, LLM은 선택 고도화입니다.
- Birthday Wish Money는 P2 Social Impact 확장 사례로만 다룹니다.
- 근거 슬라이드에는 실제 확보 전까지 임의 수치를 넣지 않습니다.

## Repository Structure

```text
finmate/
  README.md
  docs/
    00_project_overview.md
    01_presentation_plan_v1.0.md
    02_screen_state_spec_v1.0.md
    03_api_handoff_v1.0.md
    04_erd_data_dictionary_v1.0.md
    05_roadmap_decision_criteria_v1.0.md
    06_mock_data_mapping_v1.0.md
    07_demo_rehearsal_script.md
    08_qa_checklist.md
    09_glossary.md
    10_evidence_collection_plan.md
  openapi/
    finmate-p0-v1.0.yaml
  seed/
  requests/
    finmate-p0-v1.0.http
  scripts/
    validate_contract.py
    demo-reset.md
  tasks/
    development_backlog_v1.0.md
```

## Role-Based Reading Guide

| 역할 | 먼저 읽을 문서 |
| --- | --- |
| 발표자 | `docs/00_project_overview.md`, `docs/01_presentation_plan_v1.0.md`, `docs/07_demo_rehearsal_script.md` |
| 프론트엔드 | `docs/02_screen_state_spec_v1.0.md`, `openapi/finmate-p0-v1.0.yaml`, `requests/finmate-p0-v1.0.http` |
| 백엔드 | `docs/03_api_handoff_v1.0.md`, `docs/04_erd_data_dictionary_v1.0.md`, `seed/README.md` |
| QA/데모 운영 | `docs/08_qa_checklist.md`, `scripts/demo-reset.md`, `scripts/validate_contract.py` |
| 기획/팀 리드 | `docs/05_roadmap_decision_criteria_v1.0.md`, `docs/09_glossary.md`, `docs/10_evidence_collection_plan.md` |

## Validate The Contract

```bash
python3 scripts/validate_contract.py
```

성공 문구:

```text
FinMate P0 contract validation passed
```

검증 스크립트는 OpenAPI 12개 API, seed 고정 ID, 시뮬레이션 수치, 개인정보 철회 대상, 화면에서 필요한 응답 필드, 금지된 버전/표현 회귀를 확인합니다.

## Rehearse The Demo

1. `scripts/demo-reset.md` 기준으로 데모 상태를 초기화합니다.
2. `requests/finmate-p0-v1.0.http`의 12단계 P0 흐름을 순서대로 확인합니다.
3. 발표 리허설은 `docs/07_demo_rehearsal_script.md`의 7분 타임박스를 따릅니다.
4. 최종 점검은 `docs/08_qa_checklist.md`로 진행합니다.

## Versioning

이 레포의 공개 기준선은 `v1.0.0`입니다. 이전 내부 작업 번호는 공개 문서에서 사용하지 않습니다.

