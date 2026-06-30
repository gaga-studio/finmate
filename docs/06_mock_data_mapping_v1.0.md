# FinMate Mock 데이터 매핑표 v1.0

작성일: 2026-06-30  
용도: 발표 데모에서 항상 같은 화면/값이 나오도록 seed data와 ID를 고정한다.

범위: 공개 v1.0은 발표 데모와 개발 착수를 위해 seed JSON, HTTP request template, demo reset runbook, validator를 포함한다.

## 0. 원본 합성 데이터셋 연결

FinMate v1.0 seed는 `gaga-studio/financial-sns-mydata-202606` 합성 데이터셋을 출처로 삼되, 원본 전체를 공개 레포에 복사하지 않는다.

| FinMate ID | Source Persona | 사용 방식 |
| --- | --- | --- |
| `demo-user-001` | `P001` | 데모 사용자 feature의 기준 |
| `own-portfolio-001` | `P001` | 본인 공개 미리보기와 철회 path의 마스킹 snapshot |
| `peer-portfolio-023` | `P003` | 또래 비교 카드와 비상금 루틴형 사례 |

발표용 seed 값은 원본 데이터를 P0 데모에 맞게 정규화한 값이다. Source persona는 페르소나 성격과 거래 맥락의 출처이고, seed JSON은 P0 화면과 계산 흐름을 안정적으로 보여주기 위한 정규화 실행 데이터다. 따라서 `data/p0/`의 feature와 ledger 샘플은 출처와 방향성을 설명하는 subset이며, 전체 원본 ledger 재현을 목적으로 하지 않는다.

원본 feature 값과 FinMate seed 정규화 값의 차이는 `data/p0/normalization-map.json`에서 확인한다.

## 1. 고정 ID

| 항목 | 값 | 설명 |
| --- | --- | --- |
| 데모 사용자 | `demo-user-001` | 로그인 사용자 |
| 연결 persona | `P001` | demo-user-001의 합성 마이데이터 원본 |
| 또래 포트폴리오 | `peer-portfolio-023` | 비교 대상: 비상금 루틴형 B |
| 본인 공개 미리보기 | `own-portfolio-001` | SET-01 철회 테스트 대상 |
| diagnosisId | `diag-001` | 30초 진단 결과 |
| onboardingToken | `onb-token-001` | ONB-02 내부 API 인증 |
| mydataConnectionId | `mydata-mock-001` | mock 마이데이터 연결 |
| privacySettingsId | `privacy-001` | 개인정보 설정 |
| accessToken | `demo-token` | HOME-01 이후 API 인증 |
| comparisonId | `cmp-001` | 1:1 비교 |
| simulationId | `sim-001` | 3개월 시뮬레이션 |
| missionId | `mis-001` | 오늘의 미션 |

## 2. 사용자/또래 값

| 지표 | demo-user-001 | peer-portfolio-023 |
| --- | ---: | ---: |
| 월소득 | 2,200,000 | 2,300,000 |
| 월지출 | 1,680,000 | 1,450,000 |
| 월저축 | 180,000 | 480,000 |
| 저축률 | 0.08 | 0.21 |
| 고정비 비중 | 0.42 | 0.38 |
| 현금성 자산 | 400,000 | 1,800,000 |
| 월 필수지출 | 1,000,000 | 1,000,000 |
| 비상금 준비율 | 0.4 | 1.8 |

## 3. Home peerTeaser

```json
{
  "portfolioId": "peer-portfolio-023",
  "cohortLabel": "사회초년생 · 월소득 200만 원대 · 1인 가구",
  "sampleSize": 42,
  "message": "비슷한 또래보다 비상금 준비율이 낮은 편입니다."
}
```

## 4. Portfolio snapshot

`peer-portfolio-023`은 `AnonymousPortfolio` snapshot JSON에서 렌더링한다.

```json
{
  "financialSummaryJson": {
    "savingsRateRange": "20-25%",
    "emergencyFundMonthsRange": "1.5-2.0개월",
    "fixedCostRatioRange": "35-40%"
  },
  "featureSnapshotJson": {
    "savingsRate": 0.21,
    "fixedCostRatio": 0.38,
    "cashAssets": 1800000,
    "essentialMonthlyExpense": 1000000,
    "emergencyFundMonths": 1.8
  },
  "routineCardsJson": [
    {
      "title": "월급일 다음 날 자동이체",
      "description": "고정 지출 전에 비상금 계좌로 먼저 옮겨요.",
      "tag": "AUTO_TRANSFER"
    },
    {
      "title": "배달비 주 1회 줄이기",
      "description": "변동비를 줄여 비상금 여력을 만들어요.",
      "tag": "SPENDING_CONTROL"
    }
  ]
}
```

## 5. 비교/시뮬레이션 고정 결과

| 항목 | 값 |
| --- | --- |
| `mainGap` | `EMERGENCY_FUND` |
| `normalizedGap` | 0.47 |
| `similarityScore` | 0.84 |
| `similarityScoreMode` | `DEMO_NORMALIZED` 문서상 의미 |
| `incomeProximityScore` | 1.0 |
| `lifeStageProximityScore` | 1.0 |
| `demoContextScore` | 0.2 |
| `goalAlignmentScore` | 1.0 |
| `scenarioType` | `FOLLOW_PEER_ROUTINE` |
| `periodMonths` | 3 |
| `monthlyAdditionalSaving` | 100,000 |
| `before.emergencyFundMonths` | 0.4 |
| `after.emergencyFundMonths` | 0.7 |
| `missionTemplateId` | `MISSION_AUTO_TRANSFER_SMALL` |
| `mission.title` | `이번 달 비상금 자동이체 10만 원 설정하기` |
| `triggerSource` | `SIMULATION` |
| `recommendationSource` | `RULE_BASED` |

## 6. Privacy path

| 단계 | 대상 | 기대 결과 |
| --- | --- | --- |
| 공개 미리보기 | `own-portfolio-001` | displayName, hiddenFields, exposedFields 표시 |
| 공개 범위 수정 | `privacy-001` | GET과 같은 response shape 반환 |
| 철회 | `own-portfolio-001` | `status = WITHDRAWN` |
| 철회 후 조회 | `own-portfolio-001` | `410 PORTFOLIO_NOT_AVAILABLE` |
| 또래 카드 조회 | `peer-portfolio-023` | 계속 `200 OK` |

## 7. 발표 리셋 기준

- 데모 시작 전 `demo-user-001`의 mission, simulation, comparison, privacy 상태를 seed 기준으로 되돌린다.
- LLM provider는 꺼져 있어도 된다.
- rule-based fallback은 항상 `MISSION_AUTO_TRANSFER_SMALL`을 반환해야 한다.
- 중복 미션 생성은 `simulationId + missionTemplateId + localDate` 기준으로 막고 기존 `mis-001`을 반환한다.

## 8. 포함된 실행 준비물

| 준비물 | 이유 |
| --- | --- |
| `seed/users.json` | demo-user-001 고정 |
| `seed/personas.json` | P001 고정 |
| `seed/portfolios.json` | peer/own portfolio snapshot 고정 |
| `seed/mission-templates.json` | 월 10만 원 미션 템플릿 고정 |
| `scripts/demo-reset.md` | 발표 리허설 반복 안정화 |
| `requests/finmate-p0-v1.0.http` | P0 12개 API 순서 검증 |
| `scripts/validate_contract.py` | 계약 회귀 방지 |
