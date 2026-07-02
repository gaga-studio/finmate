# 미션 흐름 QA 리포트

검증일: 2026-07-02  
기준 계정: `p001@synthetic.finmate.local / password123!`  
기준 데이터: synthetic import QA payload, 앱 기준일 `2026-06-12`

## 결론

미션 흐름은 이제 버튼 클릭으로 완료되지 않고, 행동 데이터 평가 결과로만 완료된다.

- 기존/imported 미션은 기존 합성 행동 데이터로 평가된다.
- 사용자가 새로 추가한 추천 미션은 추가 시점 이후의 행동 데이터만 평가한다.
- 추천 미션 추가 이벤트는 DB에는 남지만 기록 탭 기본 미션 기록에는 보이지 않는다.
- 기록 캘린더의 성공 표시는 `DONE + DATA_EVALUATION + SUCCESS` 이벤트 기준으로만 표시된다.

## 화면 흐름

| 버튼/진입점 | 이동 화면 | API 상태 | 기대 결과 | 실제 결과 |
| --- | --- | --- | --- | --- |
| 하단 탭 `미션` | `/missions` | `GET /api/app/missions` | 오늘의 미션 1개, 진행 중, 완료, 미션 추가, 포인트 섹션 표시 | 통과. 오늘의 미션은 `고정 지출 5% 줄이기`, 완료 미션은 식비/저축 2개로 분리 |
| 오늘의 미션 hero | `/missions/mission-fixed-cost` | `GET /api/app/missions/{missionId}` | 완료 버튼 없이 조건, 판정, 근거 표시 | 통과. `완료 조건`, `평가 기간`, `현재 판정`, `근거 데이터` 표시 |
| 진행 중 빈 카드 | `/missions/add` | `GET /api/app/missions/add` | 진행 미션이 오늘의 미션 외에 없을 때 깨진 빈 섹션 대신 안내 카드 표시 | 통과. “진행 중인 미션을 더 추가해보세요” 노출 |
| `미션 추가하기` | `/missions/add` | `GET /api/app/missions/add` | 추천 미션 카드별 선택 가능, 하단 모호한 첫 추천 CTA 없음 | 통과. 각 추천 카드가 추가 동작을 수행하고 `첫 추천 미션 추가하기` 미노출 |
| 추천 카드 `이번 달 비상금 자동이체 10만 원 설정하기` | `/missions/mission-auto-transfer-small` | `POST /api/app/missions/add/MISSION_AUTO_TRANSFER_SMALL` 후 상세 조회 | 추가 직후 과거 저축 거래로 즉시 완료되지 않음 | 통과. `DATA_NEEDED`, “미션 추가 이후 새 행동 데이터가 아직 없어요.” 표시 |
| 하단 탭 `미션` 재진입 | `/missions` | `GET /api/app/missions` | 새 추천 미션은 진행 중 섹션에 표시 | 통과. `이번 달 비상금 자동이체 10만 원 설정하기`가 0% 진행 중으로 표시 |
| 하단 탭 `기록` | `/records` | `GET /api/app/records?month=2026-06` | 성공 이벤트만 캘린더/오늘 미션 기록에 표시, `추가됨` 미노출 | 통과. 초록 점은 성공 기록 기준, `추가됨` 없음 |
| 캘린더 날짜 `12` | `/records/2026-06-12` | `GET /api/app/records/2026-06-12` | 예산, 지출, 성공 미션, 포인트 기록 표시 | 통과. `추가됨` 없이 성공 미션과 포인트만 표시 |
| 미션 상단 아이콘 | `/missions/next-goals` | `GET /api/app/missions/next-goals` | 다음 목표 제안 화면 표시 | 통과. 추천 다음 목표 화면 표시 |

## 주요 확인 사항

- `오늘 실천 기록하기` 버튼 없음.
- `/missions/:missionId/feedback`로 이동하는 사용자 흐름 없음.
- 추천 미션 추가 직후 포인트가 증가하지 않음.
- 추천 미션 추가 직후 `missions.status`는 `ACTIVE` 유지.
- 추천 미션 추가 이벤트 `ADDED + USER_ACTION`은 기록 화면에서 숨김.
- 행동 데이터 성공 이벤트 `DONE + DATA_EVALUATION + SUCCESS`만 기록 화면에 표시.

## 캡처

로컬 QA 캡처 위치:

- `output/playwright/mission-flow-product-qa/01-home.png`
- `output/playwright/mission-flow-product-qa/02-missions-before-add.png`
- `output/playwright/mission-flow-product-qa/03-mission-detail-fixed-cost.png`
- `output/playwright/mission-flow-product-qa/04-mission-add.png`
- `output/playwright/mission-flow-product-qa/05-added-mission-detail.png`
- `output/playwright/mission-flow-product-qa/06-missions-after-add.png`
- `output/playwright/mission-flow-product-qa/07-records.png`
- `output/playwright/mission-flow-product-qa/08-record-detail-2026-06-12.png`
- `output/playwright/mission-flow-product-qa/09-next-goals.png`

## 실행한 검증

- `./gradlew :apps:api:test`
- `npm run lint --prefix apps/web`
- `npm run build --prefix apps/web`
- `npm run e2e --prefix apps/web`
- `python3 tools/scripts/validate_app_contract.py`
- `python3 tools/scripts/validate_product_mvp.py`
- `python3 tools/scripts/validate_synthetic_import.py`
- 최신 Docker 컨테이너 기준 수동 Playwright QA
