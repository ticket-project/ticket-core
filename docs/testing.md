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
| `BootstrapArchitectureTest` | 실행 모듈이 도메인에 직접 닿지 않는지, 트리거 위치 |

새 코드의 위치가 의심스러우면 `CoreLayerArchitectureTest`부터 돌린다. 무엇을 막는지는
[architecture.md의 아키텍처 규칙](architecture.md#아키텍처-규칙)에 정리돼 있다.

`CoreDomainModuleStructureTest`는 `settings.gradle`과 다른 모듈의 파일을 **상대 경로로** 읽는다.
그래서 Gradle이 정해 주는 작업 디렉터리에서만 통과한다. IDE에서 작업 디렉터리를 바꿔 단독 실행하면
코드가 옳아도 실패하므로, 이 테스트의 실패는 Gradle로 다시 확인한 뒤에 판단한다.

## 무엇을 어디에 두는가

**source set이 테스트의 종류를 표현한다.** 이름이 아니라 실제 실행 특성으로 정한다. Spring
컨텍스트, `EntityManager`, 실제 DB/Redis가 필요하면 `src/integrationTest`이고, 그 밖에는
`src/test`다. 클래스 이름에 `Integration`이 붙어 있어도 판정 기준은 실행 특성이다.

| source set | 두는 것 | 두지 않는 것 |
| --- | --- | --- |
| `core-domain/src/test` | 엔티티, 값 객체, 상태 전이, 정책, 불변식 | Spring 컨텍스트, DB, Redis |
| `core-app/src/test` | use case와 오케스트레이션. 외부 port는 mock/fake | Spring 컨텍스트, DB, Redis |
| `core-api/src/test` | Controller 계약, 요청 binding, Bean Validation, 보안, 예외 응답. API slice test 허용 | 실제 인프라 |
| `core-infra/src/test` | 조회 helper, codec, key formatter, 기술 값 변환, 외부 client를 mock한 adapter 단위 테스트 | `@SpringBootTest`, `EntityManager`, 실제 DB/Redis |
| `core-infra/src/integrationTest` | JPA, Querydsl, RepositoryAdapter, Spring 트랜잭션, `@TransactionalEventListener`, Redis/Redisson, Testcontainers, H2 영속성 | 업무 규칙 단위 테스트 |
| `bootstrap/src/integrationTest` | 전체 Spring 컨텍스트, 실제 빈 배선, 기동 smoke test | 개별 클래스 단위 검증 |

별도 `integration-test` Gradle 모듈은 두지 않는다. 위 두 source set과 `integrationTest` task가
원본이다. `core-domain`에는 `testFixtures`가 없다. JPA·Querydsl 테스트 지원 코드는
`core-infra/src/integrationTest`의 `support` 패키지가 소유한다.

- `ReadRepositoryTestSupport`: H2 컨텍스트와 엔티티 저장 헬퍼
- `InfraReadRepositoryTestSupport`: 위에 조건·정렬·커서 헬퍼 빈을 더한 Querydsl 조회 베이스

`core-app`의 `testFixtures`에는 `RecordingLockManager`가 남아 있고 실제로 쓰인다.

## 통합 테스트

실제 인프라에 붙어야 하는 검증이 여기 온다. 실행 조건과 Docker 주의는 `/verify`를 본다.

- `core-infra`: JPA·Querydsl 조회와 RepositoryAdapter(H2), 커밋 후 리스너, Redis key·TTL·
  expiration listener·분산락(Testcontainers)
- `bootstrap`: `ApplicationContextLoadTest` — 전체 컨텍스트가 실제로 조립되는지

Redis key, TTL, expiration listener, Redisson 관련 변경은 단위 테스트만으로 확인했다고 보지 않는다.
**Docker가 없어 `integrationTest`를 돌리지 못했다면 단위 테스트 통과로 대체하지 않고 미검증으로
보고한다.**

**모듈 사이로 빈을 옮기는 변경은 `:bootstrap:integrationTest`까지 돌린다.** 단위 테스트는 각 클래스를
직접 생성하므로 빈 배선이 깨져도 통과한다. 기동 실패는 컨텍스트를 통째로 띄워야 드러난다.

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
- Redis나 DB에 실제로 붙어야 하는 검증은 `core-infra`의 `src/integrationTest`에 둔다. 단위 테스트에 섞지 않는다.
- 빈 배선과 기동 여부는 `bootstrap`의 `src/integrationTest`에 둔다.
- 검증 규칙을 고정할 때는 계층을 맞춘다. API DTO와 Controller 계약은 `core-api/src/test`,
  `UseCase.Input` 계약은 `core-app/src/test`, 업무 불변식은 `core-domain/src/test`다.
  같은 규칙을 두 계층에서 동시에 고정하지 않는다.
- 주문·hold 흐름을 바꿨다면 성공 경로만 두지 않고 **취소, 만료, 후처리 실패, 순서 역전**을 함께 고정한다.
- 트랜잭션 경계 자체가 계약인 지점은 그 사실을 테스트로 고정한다. 기존 예시로
  `execute는_DB_트랜잭션을_직접_시작하지_않는다`, `주문_저장_메서드는_트랜잭션으로_실행된다`가 있다.

## 결과를 보고할 때

`/verify` 스킬의 "결과를 보고할 때"를 따른다. 돌리지 않은 범위를 밝히는 것과, 검증 실패 상태로
커밋하지 않는 것이 핵심이다.
