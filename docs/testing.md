# 테스트 기준

이 문서는 무엇을 검증할지 고르는 기준과 새 테스트를 추가할 때의 관례를 정리한다. 모듈 경계는
[architecture.md](architecture.md), 구현 흐름은 [development.md](development.md), 실행 환경은
[operations.md](operations.md)를 함께 본다.

핵심 규칙 하나 — **전체를 돌리는 것은 기본값이 아니다.** 바꾼 것에 닿는 검증부터 좁게 실행하고,
전체는 push 직전이나 원인을 모를 때 돌린다.

## 무엇을 돌릴지

| 상황 | 실행 |
| --- | --- |
| 컴파일 여부만 빠르게 보고 싶다 | `./gradlew :core:core-api:compileJava` |
| 엔티티, 값 객체, 도메인 정책, `*Finder`를 고쳤다 | `./gradlew :core:core-domain:test` |
| use case, 트랜잭션 경계, 조립, 조회 view를 고쳤다 | `./gradlew :core:core-app:test` |
| Controller, DTO, security, 설정을 고쳤다 | `./gradlew :core:core-api:test` |
| Querydsl 조회, JWT, 암호화, scheduler를 고쳤다 | `./gradlew :core:core-infra:test` |
| 모듈 경계, 패키지 위치, `build.gradle`을 건드렸다 | 구조 테스트 (아래 참조) |
| Redis adapter, key, TTL, expiration listener를 고쳤다 | `./gradlew :core:core-infra:integrationTest` (Docker 필요) |
| 특정 테스트만 보고 싶다 | `./gradlew :core:core-app:test --tests "com.ticket.core.app.order.*"` |
| 배포 산출물까지 확인한다 | `./gradlew clean :core:core-api:bootJar -x test` |
| push·PR 직전 | `./gradlew test :core:core-infra:integrationTest :core:core-api:bootJar` (CI와 같은 명령) |
| 문서만 바꿨다 | Java 빌드 대신 `rg -n "찾을_문구"` 와 `git diff --check` |

Windows PowerShell에서는 `.\gradlew.bat`을 사용한다.

## 구조 테스트

모듈 경계와 의존 방향을 실제로 강제하는 테스트다. 구조를 건드렸다면 이것부터 돌린다.

```bash
./gradlew :core:core-api:test --tests "com.ticket.core.CoreLayerArchitectureTest"
./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainArchitectureTest"
./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"
./gradlew :core:core-api:test --tests "com.ticket.core.CoreApiArchitectureTest"
```

`CoreLayerArchitectureTest`가 계층 의존 방향을 검사하는 본체다. `core-api`만 네 모듈을 모두
클래스패스에 두기 때문에 전체 방향을 한곳에서 본다. 새 코드의 위치가 의심스러우면 이것부터 돌린다.

무엇을 막는지는 [architecture.md의 아키텍처 규칙](architecture.md#아키텍처-규칙)에 정리돼 있다.

`CoreDomainModuleStructureTest`는 `settings.gradle`과 다른 모듈의 파일을 **상대 경로로** 읽는다.
그래서 Gradle이 정해 주는 작업 디렉터리에서만 통과한다. IDE에서 작업 디렉터리를 바꿔 단독 실행하면
코드가 옳아도 실패하므로, 이 테스트의 실패는 Gradle로 다시 확인한 뒤에 판단한다.

## 통합 테스트

`core:core-infra`에만 별도 sourceSet이 있다.

```bash
./gradlew :core:core-infra:integrationTest
```

- Testcontainers를 사용하므로 **Docker가 실행 중이어야 한다.** Docker가 없으면 실패의 원인이 코드가 아니다.
- `check`가 `integrationTest`에 의존하므로 `check`를 부르면 Docker 없이 실패한다.
- Redis key, TTL, expiration listener, Redisson 관련 변경은 단위 테스트만으로 확인했다고 보지 않는다.

## 새 테스트를 추가할 때

이 저장소의 관례는 아래와 같다. 테스트 파일 대부분이 이 형태다.

- 테스트 클래스에 `@SuppressWarnings("NonAsciiCharacters")`를 붙이고 **메서드 이름을 한국어로** 쓴다.

  ```java
  @SuppressWarnings("NonAsciiCharacters")
  class CreateOrderUseCaseTest {

      @Test
      void 유효한_요청이면_hold와_주문을_생성한다() { }

      @Test
      void 진행중인_pending_주문이_있으면_예외를_던진다() { }
  }
  ```

- `@DisplayName`과 `@Nested`는 사용하지 않는다. 저장소 전체에서 사용 사례가 없다.
- 테스트 클래스 이름은 대상 클래스 이름 + `Test`로 맞춘다. Controller 계약 테스트는 `...ContractTest`를 쓴다.
- 도메인 규칙과 use case는 Spring 컨텍스트 없이 검증한다. port는 fake나 mock으로 대체한다.
- Redis에 실제로 붙어야 하는 검증은 `core-infra`의 `src/integrationTest`에 둔다. 단위 테스트에 섞지 않는다.
- 주문·hold 흐름을 바꿨다면 성공 경로만 두지 않고 **취소, 만료, 후처리 실패, 순서 역전**을 함께 고정한다.
- 트랜잭션 경계 자체가 계약인 지점은 그 사실을 테스트로 고정한다. 기존 예시로
  `execute는_DB_트랜잭션을_직접_시작하지_않는다`, `주문_저장_메서드는_트랜잭션으로_실행된다`가 있다.

## 결과를 보고할 때

- 통과·실패 수를 그대로 적는다. "대부분 통과" 같은 요약은 쓰지 않는다.
- **돌리지 않은 범위를 밝힌다.** "core-domain 통과"와 "전체 통과"는 다른 말이다.
  통합 테스트를 돌리지 않았다면 그 사실을 적는다.
- 실패가 이번 변경 때문인지 기존 상태인지 구분한다. 판단이 애매하면 변경 전 커밋에서 같은 명령을 돌려 비교한다.
- Docker 미실행, Gradle 캐시, 작업 디렉터리처럼 코드와 무관한 원인으로 실패했다면 그 사실을 먼저 적는다.
  환경 문제를 코드 결함으로 보고하지 않는다.
- 검증에 실패한 상태로 커밋하지 않는다. 테스트를 건너뛰거나 `--no-verify`로 우회하지 않는다.
