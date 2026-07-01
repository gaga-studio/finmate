# 레포 구조와 데이터 흐름

## 디렉터리 역할

```text
apps/api
apps/web
contracts/openapi/current
contracts/openapi/legacy
contracts/http/legacy
fixtures/app-seed
fixtures/mydata-samples
fixtures/dataset-manifests
tools/scripts
docs
```

- `apps/api`: Spring Boot API, 인증, DB access, 제품 서비스 조립
- `apps/web`: React/Vite 모바일 웹/PWA
- `contracts/openapi/current`: 현재 제품형 MVP 계약
- `contracts/openapi/legacy`: P0/P1 발표용 계약 보관
- `contracts/http/legacy`: 과거 P0 수동 요청 파일
- `fixtures/app-seed`: 로컬 개발과 테스트 계정 bootstrap에 쓰는 seed
- `fixtures/mydata-samples`: 합성 MyData 원본 레포와 FinMate seed의 연결 자료
- `fixtures/dataset-manifests`: 외부 합성 MyData dataset source/version manifest
- `tools/scripts`: reset, bootstrap, synthetic import, contract validation

## Runtime Data

일반 사용자의 런타임 상태는 Postgres에 저장합니다. `fixtures/app-seed`는 새 사용자에게 자동으로 fake 데이터를 넣는 용도가 아니라, 개발/QA 계정과 legacy P0/P1 호환 흐름을 재현하기 위한 기준본입니다.

Docker API 이미지는 `fixtures/app-seed`를 `/app/fixtures/app-seed`로 복사하고, `FINMATE_SEED_DIR`로 위치를 지정합니다.

## Synthetic MyData Import

외부 합성 데이터셋 `gaga-studio/financial-sns-mydata-202606`은 FinMate 레포에 직접 포함하지 않습니다. 필요할 때 `tools/scripts/import-synthetic-mydata.py`가 dataset checkout을 읽고 `/api/dev/import-synthetic-dataset`로 보내 Postgres에 synthetic user state를 생성합니다.

자세한 규칙은 `docs/architecture/synthetic-data-provider.md`를 봅니다.

## Generated Artifacts

`.superloopy/`, Playwright 결과물, 임시 캡처는 Git에 올리지 않습니다. 리뷰나 발표에 필요한 선별 캡처만 `docs/assets/screenshots/`에 둡니다.
