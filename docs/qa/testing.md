# 실행과 검증

## Docker 실행

```bash
docker compose up --build
```

접속:

```text
http://localhost:5173/login
```

상태 확인:

```text
http://localhost:8080/health
```

## 테스트 계정

```bash
tools/scripts/reset-product-db.sh
tools/scripts/bootstrap-test-account.sh
```

기본 계정:

```text
minjun@finmate.local / password123!
```

## 자동 검증

```bash
python3 tools/scripts/validate_contract.py
python3 tools/scripts/validate_app_contract.py
python3 tools/scripts/validate_product_mvp.py
python3 tools/scripts/validate_synthetic_import.py
./gradlew :apps:api:test
npm run lint --prefix apps/web
npm run build --prefix apps/web
npm run e2e --prefix apps/web
```

E2E는 API와 Web이 실행 중이어야 합니다. 다른 주소로 테스트할 때는 `PLAYWRIGHT_BASE_URL`, `PLAYWRIGHT_API_URL`을 사용합니다.

## 수동 QA 체크

- 회원가입 후 온보딩 4단계가 홈으로 이어지는지 확인
- 새 계정 홈에 온보딩 기반 미션, 예산, 지출 요약, 자산 현황이 보이는지 확인
- bootstrap 계정 홈에는 미션, 예산, 자산, 친구 피드, 생일 이벤트가 보이는지 확인
- synthetic import 후 `p001@synthetic.finmate.local / password123!` 계정이 홈, 비교, 미션, 기록, 프로필 데이터를 표시하는지 확인
- 미션 완료 후 기록과 포인트 내역이 갱신되는지 확인
- 생일펀드 참여 후 포인트 원장과 완료 화면이 일관적인지 확인
- 로그아웃 후 보호 라우트가 로그인 화면으로 돌아가는지 확인
