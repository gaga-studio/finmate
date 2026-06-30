# FinMate Dataset Link

이 폴더는 FinMate v1.0 P0 데모 seed가 어떤 합성 데이터셋을 기준으로 만들어졌는지 설명한다.

FinMate 공개 레포에는 원본 데이터셋 전체를 복사하지 않는다. 발표와 개발 착수에 필요한 최소 P0 subset만 포함하고, 전체 생성물은 원본 레포를 기준으로 참조한다.

## Source Dataset

| 항목 | 값 |
| --- | --- |
| 원본 레포 | `gaga-studio/financial-sns-mydata-202606` |
| 링크 | https://github.com/gaga-studio/financial-sns-mydata-202606 |
| 데이터 성격 | 대한민국 청년 19~34세 합성 금융 활동 데이터 |
| 기준 월 | 2026-06 |
| 원본 규모 | 199 synthetic personas |
| 개인정보 | 실제 개인정보, 실제 거래, 실제 계좌를 포함하지 않는 합성 데이터 |

자세한 출처와 사용 정책은 `source-dataset.md`를 본다.

## Included In This Repo

| 파일 | 역할 |
| --- | --- |
| `p0/dataset-persona-map.json` | FinMate seed ID와 원본 persona ID의 대응 |
| `p0/normalization-map.json` | 원본 feature 값과 FinMate seed 정규화 값의 관계 |
| `p0/feature-matrix.demo.csv` | P0 설명에 필요한 P001/P003 feature subset |
| `p0/ledger-sample.demo.csv` | 발표 데모 맥락을 설명하는 최소 거래 샘플. 공개 레포에서는 거래처를 UI-safe label로 마스킹 |

## Not Included

- 원본 `bundles/P001..P199/` 전체
- 원본 `aggregates/ledger_all.csv`
- Excel 산출물
- 실제 마이데이터 연결 결과
- 실제 개인 금융정보

## P0 Mapping

| FinMate ID | Source Persona | 용도 |
| --- | --- | --- |
| `demo-user-001` | `P001` | 데모 사용자 기준 페르소나 |
| `own-portfolio-001` | `P001` | 본인 공개 미리보기/철회 대상의 마스킹 snapshot |
| `peer-portfolio-023` | `P003` | 비상금 루틴형 또래 사례 |

FinMate seed의 화면 수치와 시뮬레이션 값은 원본 데이터를 발표용 P0 흐름에 맞게 정규화한 값이다. 따라서 이 폴더의 subset은 출처와 방향성을 설명하는 연결 자료이며, 원본 ledger 전체를 그대로 재현하는 목적이 아니다.
