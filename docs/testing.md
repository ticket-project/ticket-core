# 테스트 기준

**테스트를 언제 돌리는지, 변경마다 무엇을 검증하는지, 새 테스트를 어떻게 쓰는지, 부하 테스트를 어떻게 안전하게
하는지**의 단일 기준이다. 각 테스트가 무엇을 고정하는지는 테스트 코드가 원본이라 여기 목록으로 옮기지 않는다.

## 테스트를 돌리는 시점

- **변경에 직접 닿는 검증부터 작업 중 실행한다.** 작은 변경은 관련 테스트 클래스부터 고르고,
  Java 변경은 `./gradlew spotlessJavaCheck`를 함께 확인한다. production 컴파일에는 NullAway 검사도 포함된다.
- **위험에 따라 범위를 넓힌다.** 모듈 경계는 구조·관련 모듈 테스트, Redis·이벤트·DB 변경은 해당
  통합·Scenario·migration 테스트, 모듈 간 조립은 컨텍스트·E2E까지 확인한다. 공통 설정·의존성·
  여러 모듈 영향이나 원인 불명 회귀는 전체 테스트(`./gradlew spotlessCheck test`)로 넓히고,
  배포 산출물까지 확인할 때는 아래 CI 기준 명령을 쓴다. 전체 빌드·테스트는 매번 강제하지 않는다.
- **필요한 검증을 마친 뒤 커밋한다.** 완료 전 최종 diff가 검증한 범위와 일치하는지 확인한다.
  실패하면 원인을 해결하고 관련 검증을 다시 실행한다. 새 변경·실패·미해결 우려가 없으면 같은
  검증을 커밋마다 반복하지 않는다.
- **문서만 바꿨으면 문서 검사만 한다.** `bash scripts/check-docs.sh`, 변경 링크·anchor·명령 근거와
  `git diff --check`를 확인한다. Java 포맷·전체 테스트는 요구하지 않는다.
- **seed는 별도로 검증한다.** seed 변경은 `./gradlew seedTest`부터 시작하고 서비스와 함께 영향을
  받으면 `./gradlew test seedTest`로 넓힌다. CI·산출물 전체 검증에도 포함한다. `SeedProdOracleTest`는
  Oracle 컨테이너가 필요하다.
- **테스트를 중간에 끊었다면 다음 실행 전에 이전 실행이 멈췄는지 확인한다.** 셸을 끊어도 Gradle
  데몬의 테스트 JVM과 Testcontainers 컨테이너는 남을 수 있다(`docker ps`, `./gradlew --status`). 겹쳐 돌면
  서로 느려져 멈춘 것처럼 보인다.
- 사용자가 특정 테스트를 요청하거나 테스트하지 말라고 하면 그 말을 따른다.

## 변경별 검증

아래 표로 변경에 맞는 최소 검증과 확대 범위를 고른다. 좁은 테스트의 통과를 다른 모듈·실제 인프라 검증의 통과로 대신하지 않는다.

실제 테스트 클래스와 패키지를 먼저 `rg --files src/test seed/src/test`로 확인한다. `--tests` 패턴이 0건을
실행해도 성공으로 오인하지 않는다. Windows PowerShell은 `./gradlew` 대신 `.\gradlew.bat`을 쓴다.

| 변경 범위 | 실행 기준 |
| --- | --- |
| 특정 업무 코드(booking 예시) | `./gradlew spotlessJavaCheck test --tests 'com.ticket.booking.order.usecase.CreateOrderUseCaseTest'`부터; 관련 모듈 전체는 `--tests 'com.ticket.booking.*'` |
| 모듈·계층·Aggregate 경계 | `./gradlew architectureTest`와 관련 module test |
| Redis key·TTL·락·만료 | 해당 Redis integration test와 Testcontainers(Docker 필요) |
| 주문·hold·이벤트 흐름 | 관련 단위·Scenario·예매 E2E 테스트(Docker 필요) |
| 빈을 모듈 사이로 옮기는 변경 | 위에 더해 `ApplicationContextLoadTest`. 단위 테스트는 각 클래스를 직접 만들어 빈 연결이 깨져도 통과한다 |
| DB migration | 해당 slicing schema test, H2/Oracle 호환 테스트(Oracle은 Docker 필요) |
| seed | `./gradlew seedTest` 및 필요시 `verifySeedNotInBootJar` |
| 배포 산출물·push 전 전체 | [CI workflow](../.github/workflows/ci.yml)의 `Test and build` 단계 명령 |
| 문서 | `bash scripts/check-docs.sh`, 변경 링크·anchor·명령 근거 확인, `git diff --check` |

`test`는 서비스 테스트만 돌리고 `seedTest`는 따로 실행한다(`check`와 CI는 둘 다 돌린다). `compileJava`는
NullAway를 함께 실행한다.

**결과 보고**: 실제로 실행한 명령과 통과·실패, 실행하지 않은 범위와 이유를 구분해 보고한다. Docker 부재 등
환경 실패를 코드 결함으로 단정하거나 단위 테스트 통과로 대체하지 않는다. Docker가 없어 Testcontainers 기반
테스트를 돌리지 못했다면 미검증으로 보고한다. 실패한 검증을 통과로 보고하거나 테스트를 건너뛰어 완료로
처리하지 않는다. Redis key, TTL, expiration listener, Redisson 관련 변경은 단위 테스트만으로 확인했다고 보지
않는다.

## 테스트를 두는 곳

별도 `integrationTest` Gradle source set과 subproject는 없다. 서비스 테스트는 모두 `src/test`에 있고,
**source set이 아니라 실행 특성**으로 종류를 나눈다. 예외는 초기 데이터 적재 프로그램(`seed/`)이다. 시드 코드가
서비스 classpath에 올라가면 `bootJar`에 섞이고 Modulith가 업무 모듈로 다시 탐지하므로 별도 source set
(`seedMain`/`seedTest`)이고 테스트도 `seed/src/test/java`에 둔다.

Spring 컨텍스트, `EntityManager`, 실제 DB/Redis가 필요하면 `@DataJpaTest`/`@SpringBootTest`/
Testcontainers를 쓰고, 그렇지 않으면 순수 단위 테스트로 둔다. 클래스 이름에 `Integration`이나 `E2E`가 붙어
있어도 판정 기준은 실행 특성이다. Testcontainers를 쓰는 테스트는 **Docker가 실행 중이어야 한다.**

| 실행 특성 | 두는 것 | 두지 않는 것 |
| --- | --- | --- |
| Spring 컨텍스트 없는 단위 테스트 | 엔티티, 값 객체, 상태 전이, 정책, 불변식, use case(외부 port는 mock/fake) | Spring 컨텍스트, DB, Redis |
| `@ApplicationModuleTest(verifyAutomatically = false)` | 모듈 STANDALONE 부트스트랩 확인. 모듈마다 최소 하나 | 전체 애플리케이션 구조 검증(그건 `ModularityTests`의 몫) |
| `@DataJpaTest` | `*QuerydslRepository`의 Querydsl 조회, Spring Data가 구현하는 domain `*Repository` | 업무 규칙 단위 테스트 |
| Spring context 없는 Hibernate 단독 검증 | 해당 모듈 소유 migration만으로 schema가 만들어지고 그 모듈 JPA 매핑이 `validate`를 통과하는지(`*SlicingSchemaTest`). 다른 모듈의 migration이 있어야만 통과하면 실패다 | 업무 규칙 단위 테스트 |
| `@SpringBootTest`(+ Testcontainers) | 전체 컨텍스트 기동, Redis/Redisson 실제 연동, 실제 HTTP로 스택을 관통하는 예매 E2E | 개별 클래스 단위 검증 |

- 구조 테스트는 DB·Redis·Docker 없이 도는 것에 `@Tag("architecture")`(ArchUnit `@AnalyzeClasses`
  클래스는 `@ArchTag("architecture")`)를 붙인다. `./gradlew architectureTest`가 그 태그만 실행하고 CI도
  전체 `test` 전에 이것을 먼저 돌린다. 새 구조 테스트를 만들면 태그를 붙인다.
- 모듈 테스트에서 구조 assertion을 중복하지 않는다. 모듈 구조 검증은 `ModularityTests` 한 곳의 책임이다.
- 외부 모듈의 공개 API는 `@MockitoBean`으로 대체하는 것이 기본이다. 실제 의존 모듈을 함께 띄워야 하면
  `@ApplicationModuleTest`의 `DIRECT_DEPENDENCIES`로 범위를 좁히고, `ALL_DEPENDENCIES`는 이유가 있을 때만
  쓴다.
- 여러 모듈의 JPA/Querydsl 테스트가 공유하는 기반 클래스는 과거 module 이름을 쓰지 않고
  `com.ticket.testsupport.persistence`에 둔다. 특정 모듈만 쓰는 fixture와 support는 해당 모듈 테스트
  패키지 아래에 둔다.
- H2에 붙는 Spring context 테스트는 `@MigratedSchema`(`com.ticket.testsupport.persistence`)를 붙여 운영과
  같은 Flyway migration으로 스키마를 만든다. `ddl-auto=create`로 entity에서 만들지 않는다 — entity에는
  유니크 제약·인덱스가 없어서 entity로 만든 스키마는 DB의 중복 거절을 재현하지 못한다.

### Modulith 이벤트 테스트

주문 생성/종료 후속 처리는 Spring Modulith의 `PublishedEvents`와 `Scenario`로 검증한다.

- `PublishedEvents`와 실제 publication 상태로 booking DB 트랜잭션 성공 시 이벤트/publication이 함께
  저장되고, rollback 시 둘 다 없는지 고정한다.
- `Scenario`로 listener 완료를 기다리고, 첫 시도 실패 후 publication FAILED, 재처리 성공 후
  COMPLETED/ARCHIVED, 재시도 상한 초과 시 자동 제외를 검증한다. 고정 clock과 deterministic fake를 쓰고
  `Thread.sleep`을 쓰지 않는다.
- 동일 `eventId`가 여러 번 전달돼도 최종 상태와 WebSocket 의미가 한 번 처리한 것과 같은지 고정한다(멱등성).

### 예매 E2E

`BookingE2ETestSupport`를 상속한다. 인증 헬퍼, 좌석 상태 조회, 커밋 후 처리를 기다리는 `pollUntil`이 여기
있고, Testcontainers Redis와 H2·기동 설정은 상위 `CoreApplicationTestSupport`가 갖는다. 데이터는
`fixture/booking-e2e-*.sql`이 만들고 `@Sql`이 메서드마다 초기화한다.

**모듈 사이 연결을 보는 테스트다.** 응답 코드만 확인하면 단위 테스트와 다를 게 없다. 좌석 상태를 다시 조회해
DB와 Redis가 함께 맞는지 본다.

- 회차당 같은 회원은 `PENDING` 주문을 하나만 가질 수 있다. 한 테스트에서 주문을 여러 번 만들면 앞 주문을
  취소해야 한다.
- 커밋 후 처리는 `@ApplicationModuleListener`가 요청 스레드 밖에서 비동기로 끝낸다. 고정 sleep 대신
  `pollUntil`을 쓴다.

## 새 테스트를 추가할 때

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
- 테스트 클래스 이름은 대상 클래스 이름 + `Test`로 맞춘다. Controller 계약 테스트는 `...ContractTest`, 모듈
  STANDALONE 테스트는 `...ModuleTests`, Modulith 시나리오 테스트는 `...ScenarioTest`를 쓴다.
- 도메인 규칙과 use case는 Spring 컨텍스트 없이 검증한다. port와 구체 `*QuerydslRepository`는 fake나
  mock으로 대체한다(`*QuerydslRepository`는 interface가 아니지만 Mockito가 class도 mock한다). 다른 모듈의
  공개 API도 마찬가지로 mock/fake로 대체하고, 실제 모듈 조합이 필요하면 그 사실을 테스트 이름과
  애노테이션(`DIRECT_DEPENDENCIES`)으로 드러낸다.
- Redis나 DB에 실제로 붙어야 하는 검증은 `@DataJpaTest`/Testcontainers로 분리한다. 단위 테스트에 섞지 않는다.
- 검증 규칙을 고정할 때는 계층을 맞춘다. API DTO와 Controller 계약은 `endpoint`, `UseCase.Input` 계약은
  `usecase`, 업무 불변식은 `domain` 테스트다. 같은 규칙을 두 계층에서 동시에 고정하지 않는다.
- 외부 오류 계약의 변경 영향을 분석할 때는 응답 구조·HTTP 상태·`error.code` 값에 대한 의존을 구분하고,
  소비자 소스의 실제 사용 지점을 확인한다. 응답 봉투를 사용한다는 사실만으로 특정 오류 코드 값에 의존한다고
  판단하지 않는다.
- 주문·hold 흐름을 바꿨다면 성공 경로만 두지 않고 **취소, 만료, 이벤트 재시도, 순서 역전**을 함께 고정한다.
- 트랜잭션 경계 자체가 계약인 지점은 그 사실을 테스트로 고정한다.

## Core 부하 검증

부하 시나리오·feeder CSV·옵션·분산 실행 명령·리포트의 원본은 형제 저장소
[gatling-test README](https://github.com/ticket-project/gatling-test/blob/master/README.md)다. 로컬 workspace에서는
ticket-core와 나란히 둔 `../gatling-test`에서 실행한다. 아래는 Core 쪽에서 지킬 안전 수칙이다.

- 전용 회차·좌석·회원과 중복 없는 토큰/feeder를 준비하고, Ticket/Queue Redis를 분리하고, 양쪽 access/admission
  secret을 일치시킨다. DIRECT/QUEUE 정책과 token 경로를 확인한다. `coreBaseUrl`·`queueBaseUrl`을 따로
  지정해 잘못된 서버 호출을 막는다.
- 연속 실행 전에 PENDING 주문 만료, hold TTL, 미완료 event publication, Queue entered marker가 정리됐는지
  확인한다.
- 성능 목표(Queue 입장률)는 [Core 수용량](core-capacity.md)의 순서로 정하며 아직 확정값이 **없다**. 테스트 시작
  전에 대상 URL·사용자 수·투입 시간·전용 performanceId와 판단 기준을 승인받는다. **운영 환경 부하는 그 문서의
  수용량 측정에만**, 실사용자가 없는 시간에 전용 부하 회차로 준다.
- 판정은 실패율·p95/p99·500 응답뿐 아니라 성공한 hold/order 수가 좌석 수를 넘지 않는지, admission 경로가
  맞는지까지 본다. DB pool·Redis 지연·executor backlog 같은 서버 지표는 운영 관측 지표와 함께 본다.
- 측정 가정, 예시 값, 실제 결과, 승인된 운영 설정을 구분한다. 개별 결과는 원본 리포트와 Issue/PR에 남기고
  중요한 설계 결정에만 ADR 근거로 쓴다. 부하 잔재와 비추적 결과 파일은 사용자 확인 없이 삭제·이동하지 않는다.
