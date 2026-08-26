# 테스트 기준

이 문서는 **새 테스트를 쓸 때의 관례와 각 테스트가 무엇을 고정하는지**를 정리한다. 모듈 경계는
[architecture.md](architecture.md), 구현 흐름은 [development.md](development.md), 실행 환경은
[operations.md](operations.md)를 함께 본다.

**무엇을 돌릴지 고르는 기준과 실행 명령, 결과 보고 규칙은 `/verify` 스킬이 원본이다**
(`.claude/skills/verify/SKILL.md`). 핵심 규칙 하나 — 전체를 돌리는 것은 기본값이 아니다.

## 구조 테스트

모듈 경계와 의존 방향을 **실제로 강제하는** 테스트다. 구조를 건드렸다면 이것부터 돌린다
(명령은 `/verify`).

| 테스트 | 고정하는 것 |
| --- | --- |
| `CoreLayerArchitectureTest` | 계층 의존 방향 전체. `core-api`만 네 모듈을 모두 클래스패스에 두므로 여기서 한번에 본다 |
| `CoreDomainArchitectureTest` | `core-domain`의 Spring 사용 범위 |
| `CoreDomainModuleStructureTest` | 도메인 파일 배치 |
| `CoreApiArchitectureTest` | `core-api`의 의존 제약 |

새 코드의 위치가 의심스러우면 `CoreLayerArchitectureTest`부터 돌린다. 무엇을 막는지는
[architecture.md의 아키텍처 규칙](architecture.md#아키텍처-규칙)에 정리돼 있다.

`CoreDomainModuleStructureTest`는 `settings.gradle`과 다른 모듈의 파일을 **상대 경로로** 읽는다.
그래서 Gradle이 정해 주는 작업 디렉터리에서만 통과한다. IDE에서 작업 디렉터리를 바꿔 단독 실행하면
코드가 옳아도 실패하므로, 이 테스트의 실패는 Gradle로 다시 확인한 뒤에 판단한다.

## 통합 테스트

`core:core-infra`에만 별도 sourceSet(`src/integrationTest`)이 있다. Redis에 실제로 붙어야 하는
검증이 여기 온다. 실행 조건과 Docker 주의는 `/verify`를 본다.

Redis key, TTL, expiration listener, Redisson 관련 변경은 단위 테스트만으로 확인했다고 보지 않는다.

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

`/verify` 스킬의 "결과를 보고할 때"를 따른다. 돌리지 않은 범위를 밝히는 것과, 검증 실패 상태로
커밋하지 않는 것이 핵심이다.
