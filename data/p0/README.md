# P0 Demo Dataset Subset

이 폴더는 FinMate v1.0 P0 데모에서 원본 합성 데이터셋과 연결되는 최소 자료만 담는다.

## Files

| 파일 | 설명 |
| --- | --- |
| `dataset-persona-map.json` | FinMate seed ID와 원본 persona ID 매핑 |
| `normalization-map.json` | P001/P003 source feature와 FinMate seed 정규화 값의 비교 |
| `feature-matrix.demo.csv` | P001/P003의 핵심 feature subset |
| `ledger-sample.demo.csv` | 발표 설명용 거래 샘플. `description`은 P0 UI 원칙에 맞춰 거래처 원문 대신 마스킹 label 사용 |

## Mapping Summary

| 역할 | FinMate ID | Source Persona |
| --- | --- | --- |
| 데모 사용자 | `demo-user-001` | `P001` |
| 본인 공개 미리보기 | `own-portfolio-001` | `P001` |
| 또래 사례 | `peer-portfolio-023` | `P003` |

## Boundary

이 subset은 원본 데이터셋의 전체 복사본이 아니다. P0 계약 검증 스크립트는 `bundles/`, `aggregates/ledger_all.csv`, `.xlsx` 파일이 `data/` 아래에 들어오면 실패한다.
