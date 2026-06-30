# FinMate QA 체크리스트

## 계약 검증

- [ ] `python3 scripts/validate_contract.py`가 통과한다.
- [ ] OpenAPI version이 `1.0.0`이다.
- [ ] 공개 P0 operation 수가 12개다.
- [ ] `/api/coach/recommendations`가 OpenAPI에 없다.
- [ ] `MissionRequest`에 `source`가 없다.
- [ ] request template에서 withdraw 대상은 `own-portfolio-001`이다.

## 데모 데이터

- [ ] `demo-user-001`의 `cashLikeAssets`는 `400000`이다.
- [ ] `demo-user-001`의 `monthlyEssentialSpending`은 `1000000`이다.
- [ ] before 비상금 준비율은 `0.4`다.
- [ ] 월 추가 저축은 `100000`, 기간은 `3`개월이다.
- [ ] after 현금성 자산은 `700000`이다.
- [ ] after 비상금 준비율은 `0.7`이다.
- [ ] 미션 제목은 `이번 달 비상금 자동이체 10만 원 설정하기`다.

## 화면 흐름

- [ ] ONB-02에서 `onboardingToken`이 발급된다.
- [ ] HOME-01에서 `peerTeaser.portfolioId`가 `peer-portfolio-023`이다.
- [ ] EXP-03에서 `privacyBadges`와 `dataMode`가 보인다.
- [ ] EXP-04에서 `mainGap.type = EMERGENCY_FUND`가 보인다.
- [ ] SIM-01에서 `disclaimer`가 보인다.
- [ ] MIS-01에서 `privacySharePreview.containsAmount = false`가 보인다.
- [ ] SET-01에서 `ownPortfolioId = own-portfolio-001`이 보인다.

## 발표 안전 표현

- [ ] 실제 마이데이터 연결이라고 말하지 않는다.
- [ ] 실제 또래 1인의 금융정보라고 말하지 않는다.
- [ ] 금융상품 추천, 투자 자문, 수익 보장처럼 들리는 표현을 쓰지 않는다.
- [ ] Birthday Wish Money는 P2 Social Impact 확장으로만 말한다.
- [ ] 근거 슬라이드에는 실제 수집 전 임의 숫자를 넣지 않는다.

