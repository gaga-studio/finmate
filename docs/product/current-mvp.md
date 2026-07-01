# 현재 제품형 MVP

FinMate의 현재 기준은 발표용 클릭 데모가 아니라, 회원가입한 사용자가 온보딩부터 5탭 앱 흐름까지 실제 상태를 저장하며 사용할 수 있는 모바일 웹/PWA입니다.

## 사용자 흐름

```text
회원가입/로그인
-> 온보딩 설문과 동의
-> 홈
-> 비교/AI 코치
-> 미션 완료
-> 기록 확인
-> 친구 피드와 생일펀드
-> 프로필/공개 범위/로그아웃
```

## 구현된 기능

- 이메일/비밀번호 기반 인증
- JWT access token과 HttpOnly refresh cookie
- Postgres/Flyway 기반 사용자별 상태 저장
- 30초 설문, 개인정보 공개 동의, 마이데이터 제공 동의, 스타터 금융 루틴 생성
- 홈, 비교, 미션, 기록, 프로필 5탭
- 미션 완료와 포인트 적립
- 친구 피드, 생일펀드 참여, 포인트 원장
- `FinancialSnapshotV1 -> CoachResultV1` AI 연결 계약과 rule-based fallback
- 외부 합성 MyData 데이터셋을 개발용 synthetic 계정으로 import하는 흐름

## 아직 구현하지 않은 것

- 실제 금융기관 MyData
- 실제 결제, 송금, 정산
- 외부 AI/LLM provider
- 소셜 로그인
- 실제 푸시 알림

## 기준 계약

현재 제품 계약은 `contracts/openapi/current/finmate-v1.2-product-mvp.yaml`입니다. P0/P1 계약은 비교와 회귀 확인을 위해 `contracts/openapi/legacy/`에 보관합니다.

## 개발용 데이터

새 회원가입 사용자는 온보딩 응답을 바탕으로 첫 예산, 미션, 기록, 자산 요약이 생성됩니다. QA나 데이터 연동 검증이 필요할 때는 `tools/scripts/import-synthetic-mydata.py`로 `gaga-studio/financial-sns-mydata-202606` 데이터셋을 Postgres에 import해 `p001@synthetic.finmate.local` 같은 synthetic 계정을 사용합니다.
