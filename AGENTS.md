# Ticket AI 작업 규칙

이 파일은 AI 에이전트가 이 저장소에서 반드시 지킬 최소 규칙이다. 상세 설명은 `README.md`와 `docs/`를 따른다.

## 기본 원칙

- 모든 답변, 문서, 작업 로그는 한국어로 작성한다.
- 파일은 UTF-8, BOM 없이 유지한다.
- 기존 미커밋 변경은 사용자 작업으로 보고 되돌리지 않는다.
- 요청 범위 밖의 기능 추가, 대규모 리팩터링, 새 추상화는 하지 않는다.
- 파괴적 작업, 대량 삭제, `git reset`, `git checkout --`는 명시 요청 없이 수행하지 않는다.

## 먼저 읽을 순서

1. `README.md`
2. `docs/development.md`
3. `docs/architecture.md`
4. `docs/operations.md`
5. `settings.gradle`, 루트 `build.gradle`, 관련 모듈 `build.gradle`
6. 관련 소스와 테스트

부하 테스트 작업은 `docs/load-test.md`, `docs/load-test/`, `load-tests/gatling`을 추가로 읽는다.

## 모듈 경계

- `core/core-api`: Spring Boot 실행, controller, request/response DTO, security, WebSocket, HTTP 설정만 둔다.
- `core/core-domain`: use case, 도메인 모델, repository, query model, port를 둔다.
- `core/core-infra`: Redis, Redisson, WebSocket publisher, 외부 HTTP, AOP, Querydsl/P6Spy 구현을 둔다.
- `storage/redis-core`: Redis/Redisson 공통 의존성만 둔다.
- `support/security`: JWT와 admission token 공통 보안 유틸을 둔다.
- `support/logging`: 공통 로깅 리소스만 둔다.

`core-api`에 비즈니스 규칙이나 저장소 직접 접근을 넣지 않는다. Redis/Redisson/WebSocket/외부 HTTP 구현은 `core-infra` 또는 support 모듈에 둔다.

## 고위험 영역

- `auth`: JWT, refresh token, OAuth2, filter chain, 공개 API 노출을 확인한다.
- `hold`, `order`: 주문 시작/취소/만료, hold release, outbox, 부분 상태 전이를 확인한다.
- `performanceseat`: selection/hold 충돌, 좌석 상태 계산, WebSocket 전파를 확인한다.
- `queue`/admission: 대기열 필요 회차에서만 `X-Admission-Token`의 서명, 만료, performanceId 일치를 확인한다.
- Redis/Redisson: key naming, TTL, expiration listener, scheduler, 분산락 범위를 확인한다.

## 검증

작업 범위에 맞는 가장 좁은 명령부터 실행한다.

```powershell
.\gradlew.bat :core:core-api:compileJava
.\gradlew.bat :core:core-domain:test
.\gradlew.bat :core:core-api:test
.\gradlew.bat clean :core:core-api:bootJar -x test
```

문서만 변경한 경우 Java 빌드 대신 아래를 우선한다.

```powershell
rg -n "확인할_문구" .
git diff --check
```

## 보고

마무리 보고에는 변경 파일, 핵심 변경점, 검증 결과, 남은 리스크를 포함한다.
