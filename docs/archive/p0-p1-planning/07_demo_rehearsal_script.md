# FinMate 데모 리허설 스크립트

## 목표

7분 발표 안에서 FinMate의 핵심 흐름을 끊기지 않게 보여줍니다. 발표자는 기능을 모두 설명하기보다 “비교가 행동으로 바뀌는 순간”을 선명하게 전달합니다.

## 7분 타임박스

| 시간 | 화면/슬라이드 | 말할 내용 |
| ---: | --- | --- |
| 0:00-0:25 | 제품 정의 | FinMate는 또래 비교를 오늘의 금융 행동으로 바꾸는 앱입니다. |
| 0:25-1:10 | 문제 | 청년에게 부족한 것은 지식보다 행동 계기입니다. |
| 1:10-1:55 | 근거 placeholder | 인터뷰, 설문, 앱 비교, 공식 자료로 근거를 채울 예정입니다. 임의 수치는 넣지 않습니다. |
| 1:55-2:40 | 해결 구조 | 또래 사례, 격차, 3개월 변화, 오늘의 미션으로 이어집니다. |
| 2:40-3:25 | ONB/HOME | 30초 진단과 mock 동의 후 홈에서 또래 사례로 진입합니다. |
| 3:25-4:15 | EXP/COMPARE | 합성 또래 사례와 비교해 가장 큰 격차인 비상금 준비율을 확인합니다. |
| 4:15-5:05 | SIM/MISSION | 월 10만 원을 3개월 모으면 0.4개월에서 0.7개월로 바뀌고, 오늘의 미션으로 전환됩니다. |
| 5:05-5:50 | PRIVACY | 공개 미리보기와 철회로 개인정보 안전장치를 보여줍니다. |
| 5:50-6:30 | Social Impact | Birthday Wish Money는 P2 확장으로 생활 속 목표형 금융 경험을 만듭니다. |
| 6:30-7:00 | 하나금융 역할 | 공식 정보, 교육, 리워드, 신뢰 기반으로 청년의 첫 금융 행동을 돕습니다. |

## 데모 클릭 순서

```text
/onboarding
-> /home
-> /explore/portfolios/peer-portfolio-023
-> /explore/compare/peer-portfolio-023
-> /simulations/cmp-001
-> /missions/new/sim-001
-> /settings/privacy
```

## API 리허설 순서

`contracts/http/legacy/finmate-p0-v1.0.http`를 순서대로 실행합니다.

1. `POST /api/onboarding/diagnosis`
2. `POST /api/mydata/mock-consent`
3. `POST /api/privacy/consents`
4. `POST /api/demo/session`
5. `GET /api/home`
6. `GET /api/explore/portfolios/peer-portfolio-023`
7. `POST /api/comparisons`
8. `POST /api/simulations`
9. `POST /api/missions`
10. `GET /api/privacy/settings`
11. `PATCH /api/privacy/settings`
12. `POST /api/privacy/withdraw`
13. `GET /api/explore/portfolios/own-portfolio-001` 기대값: `410`
14. `GET /api/explore/portfolios/peer-portfolio-023` 기대값: `200`

## 실패 대응

| 상황 | 대응 |
| --- | --- |
| 미션이 중복 생성됨 | idempotency 기준으로 기존 `mis-001`을 보여주고 넘어갑니다. |
| own portfolio가 이미 철회됨 | `tools/scripts/demo-reset.md` 기준으로 `own-portfolio-001`만 복구합니다. |
| peer portfolio가 410으로 나옴 | 철회 대상이 잘못된 것입니다. `peer-portfolio-023`은 항상 공개 상태여야 합니다. |
| 시간이 부족함 | Social Impact 슬라이드는 20초로 줄이고 P0 데모 흐름을 유지합니다. |

