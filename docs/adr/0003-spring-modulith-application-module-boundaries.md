# Spring Modulith Application Module 경계로 전환한다

## 상태(2026-09-02): 채택. ADR 0001/0002를 부분적으로 supersede

기존 `bootstrap`/`core-api`/`core-app`/`core-domain`/`core-infra`/`storage`/`support` Gradle
멀티프로젝트를 단일 Gradle Spring Boot 프로젝트로 통합하고, `com.ticket`의 직접 하위 패키지를
Spring Modulith의 닫힌 Application Module(`booking`, `catalog`, `identity`, `admission`,
`showlike`, `metadata`, `shared`)로 재편했다. 계층(api/app/domain/infra)이 아니라 **업무 기능이
모듈 경계**가 된다.

이 ADR은 모듈 경계, cross-module 참조 방식, 이벤트, Flyway 소유권만 다룬다. **오류 계약은
범위 밖이다** — ADR 0002가 이미 되돌림을 기록했고(하단 [ADR 0002와의 관계](#adr-0002와의-관계)
참고), 이 ADR에서 다시 결정하지 않는다.

## 배경

`docs/architecture.md`가 기록하던 계층형 멀티 모듈(`core-api → core-app → core-domain`,
`core-infra`가 양쪽을 구현) 구조는 계층 경계는 강제했지만 업무 기능 경계는 강제하지 않았다.
같은 계층 모듈(`core-app`, `core-domain`) 안에서는 `order`, `hold`, `show`, `auth`가 서로
아무 제약 없이 참조할 수 있었다. Spring Modulith는 반대로 **기능 경계를 닫힌 모듈로 강제**하고
계층은 모듈 내부의 자유로운 배치에 맡긴다. 이 결정은 그 전환을 승인된 최종 형태로 기록한다.

## 결정

### 1. 단일 Gradle 프로젝트

`bootstrap`, `core:core-api`, `core:core-app`, `core:core-domain`, `core:core-infra`,
`storage:redis-core`, `support:error`, `support:logging` 서브프로젝트를 폐지하고 모든
production/test 소스를 루트 `src/main`, `src/test`로 합쳤다. `settings.gradle`은
`rootProject.name = 'ticket'` 한 줄만 갖는다. `integrationTest` source set과 Gradle subproject는
없다 — 통합 테스트도 `src/test`에 있고 구분은 `@DataJpaTest`/`@SpringBootTest`/Testcontainers
사용 여부로 한다.

### 2. 패키지 기반 닫힌 모듈

`com.ticket`의 직접 하위 패키지가 Application Module이다. 각 모듈 root에는
`@ApplicationModule`을 선언한 `package-info.java`와 다른 모듈이 쓸 공개 계약만 두고, 나머지
구현(web/application/domain/infrastructure 전부)은 `<module>.internal` 아래에 둔다. 어떤
모듈도 `Type.OPEN`으로 선언하지 않는다. 무엇을 어디에 두는지의 실무 판단표는 `/place-code`
스킬이 원본이다.

### 3. 승인된 의존 DAG

각 모듈 `package-info.java`의 `allowedDependencies`가 원본이다. 이 ADR을 쓰는 시점의 실제 값은
다음과 같다.

```text
booking   -> catalog, identity, admission
catalog   -> (없음)
identity  -> (없음)
admission -> (없음)
metadata  -> catalog, booking, identity
showlike  -> catalog, identity
shared    -> 모든 모듈이 참조할 수 있는 공유 자리(호출 대상 계약만, 아래 6번 참고)
config    -> identity :: security, identity :: oauth2, identity :: token, identity, booking :: websocket
             (여러 module의 internal을 참조하는 composition-root module, 아래 9번 참고)
```

순환은 없다. `catalog`/`identity`/`admission`은 다른 업무 모듈에 의존하지 않는 leaf 모듈이고,
`booking`과 `showlike`가 그 위에 얹히며, `metadata`가 세 모듈의 공개 계약을 조합만 한다. `shared`는
어떤 모듈도 참조하지 않는 leaf고, `config`는 identity/booking의 특정 internal package와 shared를
참조하지만 반대로 `config`를 참조하는 모듈은 없다.

### 4. 모듈 간 참조는 scalar ID와 공개 API로만

모듈을 넘나드는 JPA 연관관계(`@ManyToOne` 등)와 DB FK는 금지한다. 예: booking이 소유한
`PerformanceSeat`는 `performanceId`/`seatId`를 `long` 컬럼으로만 갖고, catalog의 `Performance`/
`Seat` 엔티티를 JPA로 참조하지 않는다. 필요한 조회는 상대 모듈이 공개한 인터페이스(예:
`catalog.BookingPolicyLookup`, `identity.MemberLookup`, `catalog.ShowLookup`)를 호출해 얻는다.

공개 API는 두 형태만 허용한다.

- **동기 조회/명령 인터페이스** — 작은 단위로 쪼갠 interface(`BookingPolicyLookup`,
  `AdmissionVerifier` 등)와 그 반환값인 **불변 `record` snapshot**(`BookingPolicySnapshot`,
  `ShowSummary`, `MemberStatus`, `AdmissionVerification` 등). JPA entity, Redis/JWT/Spring Web
  타입을 노출하지 않는다. 컬렉션은 defensive copy한다.
- **커밋 후 이벤트** — booking이 발행하는 `OrderStarted`/`OrderTerminated`. 아래 5번을 본다.

`metadata`처럼 code/label만 조합하는 모듈은 각 모듈이 공개한 `*Metadata` 계약(`CatalogMetadata`,
`BookingMetadata`, `IdentityMetadata`)만 주입받고 어떤 모듈의 internal enum도 import하지 않는다.

**알려진 예외(추적 중인 기술 부채)**: `showlike` 모듈은 이 원칙을 완전히 만족하지 못한다.
`identity`의 `/me/likes` 엔드포인트와 `catalog`의 공연 상세 `likeCount` 조회가 legacy
`com.ticket.core.domain.showlike.model.ShowLike`(entity, 여전히 `Member`/`Show`에
`@ManyToOne`)를 직접 참조하기 때문에, 이 부분을 옮기면 `identity ↔ showlike`,
`catalog ↔ showlike` 순환이 생긴다. write 경로(`AddShowLikeUseCase` 등)만
`com.ticket.showlike`로 옮겨 `MemberLookup`/`ShowLookup`을 쓰게 했고, 위 read 경로 관련 클래스는
의도적으로 legacy에 남겨 `com.ticket.ModularityTests`의 legacy 제외 predicate로 검증 대상에서
뺐다. 정리 순서와 옮기지 못한 정확한 클래스 목록은
`src/main/java/com/ticket/showlike/package-info.java`(대칭적으로 `catalog`/`identity`의
package-info)에 있다. **이 문서는 이 gap을 해결된 것으로 서술하지 않는다** — identity의
`/me/likes`를 showlike로 옮기거나 catalog의 `likeCount` 조회 방식을 바꾸는 별도 후속 작업이
끝난 뒤에만 완결된다.

### 5. Spring Modulith 이벤트와 JPA Event Publication Registry

기존 custom outbox(`HoldCreationOutbox`/`HoldReleaseOutbox`와 그 executor/relay)를 제거하고
`ApplicationEventPublisher.publishEvent(...)`와 `spring-modulith-starter-jpa`의 Event
Publication Registry로 대체했다. `OrderStarted`/`OrderTerminated`는 Order/OrderSeat/HoldHistory
저장과 **같은 booking DB transaction 안에서** 발행하므로 저장과 발행이 원자적이다. 커밋 이후
처리(Redis selection 정리, hold 해제, WebSocket 발행)는 `@ApplicationModuleListener`
(`BookingEventListeners`)가 맡고, 실패를 catch-and-log로 삼키지 않고 throw해 registry가 FAILED로
기록하게 한다.

운영 정책(모든 profile 공통, `application.yml`):

```yaml
spring:
  modulith:
    events:
      completion-mode: archive
      republish-outstanding-events-on-restart: false
      staleness:
        check-interval: 1m
        published: 5m
        processing: 10m
        resubmitted: 10m
```

재제출은 1분마다 최대 batch 100건·동시 4건으로 시도하고, 완료 시도 10회를 초과한 publication은
자동 재제출 대상에서 제외한다(수동 확인이 필요하다는 신호). 완료된 publication은 30일 뒤 archive
purge 대상이다. 정확한 절차는 [`docs/core-booking-lifecycle.md`](../core-booking-lifecycle.md)를
따른다. **broker externalization(`@Externalized`, 메시지 브로커)은 이번 결정의 범위가 아니다** —
JPA 기반 registry 하나만 쓴다.

**알려진 운영 리스크(추적 중)**: registry의 `EVENT_PUBLICATION.serialized_event` 컬럼은
`spring-modulith-events-jpa:2.1.1`의 `JpaEventPublication.serializedEvent`에 `@Lob`이 없어
`VARCHAR(255)`/`VARCHAR2(255 CHAR)`로 매핑된다. `OrderStarted`가 `performanceSeatIds`를 포함해
좌석 여러 개를 담으면 직렬화 JSON이 255자를 넘어 INSERT가 거부될 수 있다 — 이는 라이브러리
제약과 Task 8에서 확정한 이벤트 스키마가 만나는 지점이며, 이 ADR이 임의로 스키마를 바꾸지
않는다. 실제 다중 좌석 주문 트래픽 앞에 배포하기 전에 별도로 결정해야 한다(payload에서
`performanceSeatIds`를 빼고 listener가 재조회하게 하거나, 이 실패를 감수하며 모니터링하거나,
다른 방식). 상세 근거는
`docs/superpowers/plans/2026-09-01-ticket-core-spring-modulith.md`의 Task 11 "추적 필요" 기록을
본다.

### 6. shared module: 호출 대상 계약만 두는 자리

`com.ticket.shared`는 여러 모듈이 공유할 최소 계약을 위한 자리다. 처음에는 오류 계약을
`ProblemDetail`/`BusinessProblem` 대신 기존 전역 구조로 되돌리면서(ADR 0002 참고) class가 하나도
없었고, 이후 범용 유틸리티와 domain-free 기술 설정 두 갈래가 이 자리로 옮겨 왔다. **그중 기술
설정(`@Configuration`) 여덟 개는 다시 `config`로 옮겼다** — 아래 "왜 bean 등록을 두지 않는가"가 그
근거다.

`shared`에 두는 것은 **호출 대상 계약**뿐이다: 둘 이상의 독립 module이 의미 그대로 공유하고, 그
타입을 쓰기 위해 어떤 스타터도 추가하지 않아도 되는 타입. 현재 `UuidSupplier`,
`CorsProperties`(CORS 허용 origin `@ConfigurationProperties` 값 홀더 — 스스로 bean을 등록하지 않고,
등록은 값을 쓰는 identity의 `SecurityConfig`가 `@EnableConfigurationProperties`로 한다), 응답 봉투
4종(`ApiResponse`/`ErrorMessage`/`ResultType`/`SliceResponse`)이 있다.

**왜 bean 등록(`@Configuration`)을 두지 않는가.** 계약은 다른 module이 *불러 쓰는* 것이고
`@Configuration`은 포함하는 것만으로 *적용되는* 것이다. 둘이 한 module에 있으면 `shared`를
참조하는 쪽이 Redisson·Querydsl·Swagger·P6Spy 스택과 그 bean까지 강제로 함께 받아 "가져다 쓸 수
있는 모듈"이 성립하지 않는다. 게다가 `TicketApplication`이 `@Modulith(sharedModules = "shared")`를
선언하므로 `shared`는 **모든 `@ApplicationModuleTest`에 항상 포함된다** — bean 등록이 여기 있으면
모든 module의 STANDALONE 테스트가 실제 Redis 연결까지 함께 띄우고, 그 배선이 module 테스트의
성패를 좌우한다. 그래서 `SwaggerConfig`/`P6SpyConfig`/`QuerydslConfig`/`RedissonConfig`/
`UuidSupplierConfig`/`EventPublicationMaintenance`/`SchedulingConfig`/`SystemClockConfig`를
`com.ticket.config`로 옮겼고(§9), `com.ticket.shared.internal`은 비어 사라졌다. 이 규칙은
`com.ticket.shared.SharedModulePurityTest`가 강제한다(`@Configuration`을 일부러 넣어 실패하는 것을
실측 확인).

옮기고 나서 module 테스트 두 개가 실제로 bean을 잃었다 — `CatalogModuleTests`(`JPAQueryFactory`·
`Clock`)와 `BookingModuleTests`(`Clock`)는 `@MockitoBean`으로 대체했다. STANDALONE wiring smoke
test가 진짜 DB·시계를 받지 않는 쪽이 격리에 맞고, 이 대체는 두 테스트가 이미 다른 외부 의존에
쓰던 방식과 같다.

**`shared`에 두지 못한 반례(기록 유지)**: `JpaAuditingConfig`/`SecurityContextAuditorAware`(JPA
auditing이 채우는 감사자 id)는 identity의 공개 계약 `AuthenticatedMember`를 참조한다. 처음에는
이것만 예외로 `shared`에 두려 했으나, identity가 이미 `shared`(`UuidSupplier`)를 참조하는 것과
맞물려 `identity ↔ shared` 순환이 되어 `ApplicationModules.verify()`가 실패했다
(`com.ticket.ModularityTests.verifiesModuleStructure()`에서 실측 확인). `WebConfig`/
`WebSocketConfig`/`HttpServiceConfig`/`JwtConfig`는 애초에 특정 module의 `internal`을 직접 참조해
domain-free하지도 않다. 이 여섯 개도 `com.ticket.config`가 소유한다(§9).

**아직 이 기준을 만족하지 못하는 것**: `CursorPage`는 실측상 `catalog`와 legacy `com.ticket.core`의
showlike read 경로만 쓴다("둘 이상의 독립 module" 미달). legacy가 함께 쓰는 동안 `catalog.internal`로
내리면 legacy → `catalog.internal` 참조가 새로 생기므로 이번에 옮기지 않았다. §4의 showlike read
경로 정리가 끝나는 시점에 catalog 소유로 내린다. **이 문서는 이 gap을 해결된 것으로 서술하지
않는다.**

`shared`에 두지 **않는** 것: business logic, 특정 module에만 의미 있는 동작, bean을 등록하는 코드,
그리고 여러 module의 internal을 동시에 참조해야만 배선되는 설정(§9 `config` 참고) — 마지막 것을
shared에 두면 shared가 사실상 모든 module과 결합돼 "safe to depend on without redeploy coupling"
이라는 존재 이유가 무너진다.

`package-info.java`가 애노테이션 없이 비어 있으면 javac가 `package-info.class`를 만들지 않아
Spring Modulith가 이 패키지 자체를 못 보므로, `@ApplicationModule(displayName = "Shared")`만
선언해 class 없이도 module로 잡히게 했다(지금은 class가 있어도 이 선언은 그대로 유지한다).
`TicketApplication`은 계속 `@Modulith(sharedModules = "shared")`를 선언한다.

### 7. Module-aware Flyway와 물리 데이터 경계

`spring.modulith.runtime.flyway-enabled=true`로 각 추출 모듈이 독립된
`flyway_schema_history_{module}` 이력을 갖는다. 기존 적용 이력(V2~V8, 그리고 H2/Oracle vendor
V3~V7)은 byte-for-byte 그대로 `db/migration/__root`,
`db/migration-vendor/{h2,oracle}/__root`로 옮겨 checksum을 보존했다. 모듈이 소유하는 새 schema
변경(cross-module FK 제거, scalar column 전환 등)은 `db/migration/{module}`,
`db/migration-vendor/{h2,oracle}/{module}`에 module별로 1부터 새로 버전을 매겨 추가한다. 현재
독립 이력을 가진 모듈은 `booking` 하나다. 상세 절차는 [`docs/operations.md`](../operations.md)를
따른다.

### 8. bootstrap: 영구 composition-root 예외 자리 (legacy 아님, 지금은 비어 있다)

`com.ticket.bootstrap`은 `com.ticket.core`/`storage`/`support`와 같은 predicate로 Modulith 검증에서
제외되지만, 성격은 다르다. 이들은 "아직 옮기지 못한 legacy 코드"로 다른 작업이 계속 줄이고
있고 이동이 끝나면 사라진다. `bootstrap`은 반대로 **의도적이고 영구적인 네 번째 카테고리**다 —
업무 Application Module, 공유 유틸리티/기술 설정 module(`shared`, §6), composition-root module
(`config`, §9)에 이어 필요할 때 쓰는 예외 자리다.

**현재 상태**: 처음에는 `JpaAuditingConfig`/`SecurityContextAuditorAware`(identity의
`AuthenticatedMember` 참조)가 여기 남았었지만, `com.ticket.config` module이 `@NamedInterface`로
identity/booking의 필요한 internal만 좁게 열 수 있음을 확인한 뒤(§9) 그쪽으로 다시 옮겼다. 그
결과 지금 `com.ticket.bootstrap`에는 production class가 하나도 없다.

그래도 이 자리 자체는 지우지 않는다. `config`가 `@NamedInterface`로 좁혀 열 수 없을 만큼 결합이
크거나 임시적인 cross-module 배선이 생기면(예: 여러 module의 `internal`을 광범위하게 참조해야
하는 임시 조치), Modulith 검증에서 완전히 빠지는 이 자리가 그 도피처로 남는다 — `config`보다
느슨하고 감시가 없는 대신, 남용 시 눈에 잘 띄지 않는다는 trade-off가 있다.

### 9. config: 전역 배선을 소유하는 composition-root module

`com.ticket.config`는 8번째 Application Module이다. `shared`(§6)와 반대되는 존재 이유를 갖는다 —
`shared`는 다른 module이 **호출하는 계약**만 갖고 bean을 등록하지 않으며, `config`는 정확히 그
반대로 **앱에 적용되는 전역 배선**이 있을 자리다. 두 갈래를 소유한다: (a) 특정 module의 internal을
알아야만 배선할 수 있는 설정(아래 표), (b) 어떤 business module도 참조하지 않는 domain-free 전역
기술 설정(`SwaggerConfig`, `P6SpyConfig`, `QuerydslConfig`, `RedissonConfig`, `UuidSupplierConfig`,
`EventPublicationMaintenance`, `SchedulingConfig`, `SystemClockConfig` — §6에서 `shared`로부터 옮겨
왔다). (b)는 module 결합이 없어 `shared`에 둘 수도 있어 보이지만 bean을 등록한다는 점에서 계약과
성질이 다르고, `sharedModules` 선언 때문에 모든 module 테스트에 함께 뜬다(§6).
그런 전역 기술 설정을 특정 module 소유로 두면 그 module이 나머지 module을 부당하게 참조하는
것처럼 보이게 되므로, 애초에 module 후보에서 뺀다. 그런 코드를 특정 module 소유로 두면 그
module이 나머지 module들을 부당하게 참조하는 것처럼 보이게 되므로, 애초에 module 후보에서 뺀다.
`bootstrap`(§8)과 달리 이 module은 Modulith 검증에서 제외되지 않는다 — 정식 module로 선언되고
Spring Modulith의 `@NamedInterface`로 필요한 internal package만 좁혀 열어, 무엇을 얼마나
참조하는지 `com.ticket.ModularityTests.verifiesModuleStructure()`가 계속 감시한다.

**포함 클래스와 참조 대상**:

| 클래스 | 참조 대상 | 노출 방식 |
| --- | --- | --- |
| `WebConfig` | `identity.internal.infrastructure.security`의 `AuthenticatedMemberArgumentResolver` | `@NamedInterface("security")` |
| `WebSocketConfig` | `booking.internal.infrastructure.websocket`의 `WebSocketAuthInterceptor` | `@NamedInterface("websocket")` |
| `HttpServiceConfig` | `identity.internal.infrastructure.auth.oauth2`의 `KakaoUnlinkApiClient` | `@NamedInterface("oauth2")` |
| `JwtConfig` | `identity.internal.infrastructure.auth.token`의 `JwtProperties` | `@NamedInterface("token")` |
| `JpaAuditingConfig`/`SecurityContextAuditorAware` | `identity`의 공개 계약 `AuthenticatedMember` | 일반 module 의존(`identity`) — 공개 root API라 NamedInterface 불필요 |

`allowedDependencies`는 각 이름을 `"identity :: security"` 형태로 명시해, 노출된 package 이외의
identity/booking internal은 여전히 참조하지 못한다(`com.ticket.config`의 package-info가 각
NamedInterface의 정확한 노출 범위를 문서화한다). `identity.internal.infrastructure.security`·
`auth.oauth2`·`auth.token` package에는 `config`가 실제로 쓰는 타입 하나 외에 identity 전용 구현도
함께 있어, NamedInterface가 그것까지 노출한다 — 딱 필요한 타입만 더 좁은 package로 재구성하는
작업은 하지 않았다(과한 조정으로 판단, identity/booking 소유 코드를 이 task에서 재배치하는 것은
범위 밖).

`config`는 `shared`(`CorsProperties`)도 참조한다(§6) — module 결합이 없는 shared 참조는 제한
없이 허용된다. `config`를 참조하는 다른 module은 없다(leaf) — composition root는 재사용 가능한
공개 API가 아니라 배선 지점이기 때문이다.

## 승인된 것 외에 결정하지 않은 것

- **모듈 발견 전략**은 기본값(`direct-sub-packages`)을 그대로 둔다. `explicitly-annotated`로
  바꾸는 안을 검토했지만, `@ApplicationModule` 선언을 빼먹은 미래 모듈을 조용히 통과시킬 수 있어
  되돌렸다. 대신 `com.ticket.ModularityTests`가
  `ApplicationModules.of(TicketApplication.class, <legacy 제외 predicate>)`로 아직 이동하지
  않은 `com.ticket.core`/`storage`/`support` 레거시 패키지와 `com.ticket.bootstrap`(§8, 영구
  예외)을 검증에서 제외한다. 이 predicate 중 legacy 패키지 부분은 레거시 코드가 모두 이동하면
  제거해야 할 임시 장치이고, `bootstrap` 부분은 legacy가 모두 사라진 뒤에도 남는다.
- **모듈 구조의 전체 검증 방식**(정확한 모듈 집합·DAG assertion, `Documenter`, actuator/insight
  노출, profile별 `spring.modulith.runtime.verification-enabled`)은 계속 진행 중인 별도 작업의
  범위다. 이 ADR은 검증 메커니즘 자체가 `ModularityTests`에 있다는 사실만 전제하고, 그 구현
  세부사항을 여기서 단정하지 않는다.
- **legacy `com.ticket.core`/`storage`/`support`의 완전 제거**는 이 ADR의 범위가 아니다. 남은
  코드(오류 처리, showlike read 경로)는 아직 이동 대상 후보로 남아 있다. 시드 러너(옛
  `core.infra.seed`)는 이후 정리에서 10번째 module `com.ticket.seed`로 옮겨졌다 — 특정
  module이 전유하지 않고 여러 module의 테이블을 raw SQL로 적재하므로 `bootstrap`(§8)이 아니라
  정식 module로 두고, 부하 테스트 회원만 identity가 `@NamedInterface("seed")`로 좁혀 연
  `member.command` package를 통해 만든다.
  `com.ticket.bootstrap`은 지금 비어 있지만 완전 제거 대상이 아니다 — legacy가 아니라 영구
  예외 자리이기 때문이다(§8).

## ADR 0001과의 관계

ADR 0001(Selection과 Hold를 독립으로 둔다)의 **업무 결정 자체는 바뀌지 않았다.** Selection이
UX 보조 상태이고 Hold만이 판매 정합성을 지킨다는 원칙은 이 전환 이후에도 그대로 유효하다.

다만 ADR 0001이 예시로 든 구현 클래스 이름은 이 전환으로 옮겨지거나 대체됐다.
`CreateOrderValidator`/`HoldSeatAvailabilityValidator`는
`com.ticket.booking.internal.domain.hold.command.HoldSeatAvailabilityValidator`로,
`AsyncHoldCreationPostCommitNotifier`(커밋 후 selection 정리)는 5번에서 설명한
`OrderStarted`/`OrderTerminated` 발행과 `BookingEventListeners`
(`com.ticket.booking.internal.application.BookingEventListeners`)로 대체됐다. ADR 0001의
결론 문단이 가리키는 동작(같은 좌석에 A가 Selection, B가 주문하면 B가 성공한다)은 동일하게
성립하며, 이 ADR은 그 결정을 뒤집지 않는다 — 소유 모듈과 클래스 이름만 최신화한다.

## ADR 0002와의 관계

ADR 0002(오류 계약을 모듈별로 소유하고 API에서 공통 처리한다)는 구현됐다가 사용자 결정으로
되돌려졌고, **그 되돌림 근거는 ADR 0002 자신의 갱신된 상태 문단이 이미 기록하고 있다.** 오류
처리는 `support:error` 도입 이전의 전역 `ErrorType`/`CoreException`/`ApiControllerAdvice`
(`com.ticket.core.support.exception`, `com.ticket.core.support`)로 되돌려졌고, 이 오류 처리
코드는 **아직 어떤 Application Module로도 옮겨지지 않은 legacy 코드**다(위 "승인된 것 외에
결정하지 않은 것"의 legacy 제외 predicate 대상).

이 ADR은 오류 계약을 다시 설계하지 않는다. 향후 오류 처리를 모듈이 다시 소유할지, 그 경계를
어떻게 그을지는 ADR 0002가 명시한 대로 **별도로 다시 결정한다.**
