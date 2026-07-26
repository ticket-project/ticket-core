# Ticket 저장소 작업 가이드

이 문서는 사람과 AI 에이전트가 공통으로 따르는 루트 작업 기준이다.

## 기본 원칙

- 문서 작성과 기본 응답은 한국어로 작성한다.
- 파일은 UTF-8, BOM 없이 유지한다.
- 요청 목표, 제약, 완료조건을 먼저 구체화한다.
- 요청되지 않은 기능 추가, 대규모 리팩터링, 부가 추상화는 피한다.
- 변경은 작업 범위 안에서만 수행한다.
- 기존 미커밋 변경은 사용자 작업으로 보고 되돌리지 않는다.
- 파괴적 작업은 명시적으로 요청받은 경우에만 수행한다.

## 문서 우선순위

저장소 의도는 아래 순서로 파악한다.

1. `README.md`
2. `docs/development.md`
3. `docs/architecture.md`
4. `docs/operations.md`
5. 관련 모듈의 `build.gradle`, `settings.gradle`, 테스트 코드

부하 테스트나 예매 오픈 검증은 `docs/load-test.md`와 `docs/load-test/` 하위 문서를 함께 본다.

## 저장소를 읽는 순서

1. `settings.gradle`
   - 멀티 모듈 경계를 확인한다.
2. 루트 `build.gradle`과 각 모듈 `build.gradle`
   - 의존 방향과 실행 모듈을 확인한다.
3. `docs/development.md`, `docs/architecture.md`
   - 현재 구현 상태와 모듈 경계를 확인한다.
4. `core/core-api`
   - HTTP/WebSocket 진입점, 보안, 설정을 본다.
5. `core/core-domain`
   - 비즈니스 흐름, 저장소, Redis port, 락, 만료 처리를 본다.
6. `core/core-infra`
   - Redis, WebSocket, 외부 HTTP, AOP 구현체를 본다.
7. 관련 테스트
   - ArchUnit과 도메인 테스트로 실제 강제 규칙을 확인한다.

## 모듈 경계

- `core/core-api`
  - Spring Boot 실행 모듈이다.
  - Controller, request/response DTO, security, WebSocket, HTTP 설정을 둔다.
  - 비즈니스 규칙이나 직접 저장소 접근 로직을 넣지 않는다.
- `core/core-domain`
  - use case, 도메인 모델, repository, query model, port를 둔다.
  - 기능 중심 패키지를 우선한다.
- `core/core-infra`
  - Redis, Redisson, WebSocket publisher, 외부 HTTP, AOP 같은 기술 구현을 둔다.
  - `core-domain`의 port를 구현한다.
- `storage/redis-core`
  - Redis 관련 공통 의존성을 제공한다.
- `support/logging`
  - 공통 로깅 설정을 제공한다.

## 핵심 흐름

```text
HTTP/WebSocket 요청
  -> core/core-api controller/config/security
  -> core/core-domain command/query/usecase
  -> repository(RDB) + port(store/publisher/client)
  -> core/core-infra adapter
  -> core/core-api response 또는 WebSocket message
```

## 도메인별 주의 지점

- `auth`
  - JWT, refresh token, OAuth2, security filter chain, 공개 API 노출을 확인한다.
- `hold`, `order`
  - 주문 시작/취소/만료와 hold 해제 일관성, 부분 상태 전이를 확인한다.
- `performanceseat`
  - selection/hold 충돌, 좌석 상태 계산, 실시간 브로드캐스트를 확인한다.
- `queue`
  - queue token, TTL, 만료 처리, admitted/waiting 상태 전이를 확인한다.
- Redis / Redisson
  - key naming, TTL, expiration listener, scheduler, 락 범위를 확인한다.

## 검증 명령

작업 성격에 맞게 가장 좁은 검증부터 실행한다.

```bash
./gradlew :core:core-api:compileJava
./gradlew :core:core-domain:test
./gradlew :core:core-api:test
./gradlew clean :core:core-api:bootJar -x test
```

구조나 모듈 경계를 건드리면 아래 테스트를 우선 고려한다.

```bash
./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainArchitectureTest"
./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"
```

문서만 바꾼 경우에는 Java 빌드 대신 아래를 확인한다.

```bash
rg -n "찾을_문구"
git diff --check
```

## 작업 보고

마무리 보고에는 아래를 포함한다.

1. 변경 파일
2. 핵심 변경점
3. 검증 결과
4. 남은 리스크 또는 후속 선택지

## 커밋

- 커밋 메시지는 기존 히스토리의 형식을 따른다.
- 문서 변경은 `docs: ...` 형식을 우선 사용한다.
- 기존 사용자 변경과 섞어서 커밋하지 않는다.
