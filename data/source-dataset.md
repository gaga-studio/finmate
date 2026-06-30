# Source Dataset

FinMate v1.0 P0 데모는 `financial-sns-mydata-202606` 합성 데이터셋을 출처로 삼되, 공개 레포에는 전체 데이터셋을 복사하지 않는다.

## 원본

| 항목 | 내용 |
| --- | --- |
| 레포 | `gaga-studio/financial-sns-mydata-202606` |
| URL | https://github.com/gaga-studio/financial-sns-mydata-202606 |
| 데이터셋 이름 | Financial SNS MyData Synthetic Dataset 2026-06 |
| 대상 | 대한민국 청년 만 19~34세 |
| persona 수 | 199 |
| 기준 기간 | 2026-06-01 ~ 2026-06-30 |
| 생성 seed | `20260630` |
| 검증 상태 | PASS |
| error count | 0 |
| cluster silhouette score | 0.2893 |
| min cluster size | 8 |

## 원본 데이터 구성

| 원본 경로 | 설명 |
| --- | --- |
| `bundles/P001..P199/` | persona별 profile, ledger, MyData-like JSON, social companion data |
| `aggregates/personas.jsonl` | 전체 persona 목록 |
| `aggregates/feature_matrix.csv` | 군집화와 특성 복원용 feature |
| `aggregates/ledger_all.csv` | 전체 거래 통합본 |
| `aggregates/social_edges.csv` | 금융 SNS 관계 데이터 |
| `validation/` | 검증 리포트와 스키마 점검 산출물 |

## FinMate에서 쓰는 방식

FinMate P0는 최종성과발표회에서 안정적인 데모를 보여주기 위해 전체 데이터셋이 아니라 다음 subset만 쓴다.

| FinMate 대상 | 원본 persona | 이유 |
| --- | --- | --- |
| `demo-user-001` | `P001` | 저축률이 낮은 1인가구 데모 사용자 흐름에 적합 |
| `own-portfolio-001` | `P001` | 본인 공개 미리보기와 철회 시나리오를 같은 사용자 기준으로 설명 |
| `peer-portfolio-023` | `P003` | 1인가구, 비상금 목표, 높은 저축 루틴을 가진 비교 대상 |

## 정규화 정책

원본 데이터는 데모 설계의 출처이며, FinMate seed는 발표 흐름에 맞게 정규화한다.

| 항목 | 정책 |
| --- | --- |
| 화면 수치 | 발표 중 계산이 쉽게 보이도록 seed에서 고정 |
| 시뮬레이션 | `400000 + 100000 * 3 = 700000` 흐름을 우선 |
| 개인정보 | 이름, 계좌번호, 카드번호, 주소, 정확한 거래 시각 등 식별 가능한 값은 P0 화면에 노출하지 않음 |
| 거래 샘플 | 설명용 최소 행만 보관하고 원본 전체 ledger는 복사하지 않음 |

## 안전 문구

- 모든 데이터는 합성 데이터이며 실제 개인, 실제 계좌, 실제 거래를 나타내지 않는다.
- FinMate P0 데모는 실제 마이데이터 연동을 수행하지 않고 `MOCK_MYDATA` 흐름으로만 동작한다.
- 원본 데이터셋의 전체 분석이나 재생성이 필요하면 이 레포가 아니라 원본 레포를 기준으로 확인한다.
