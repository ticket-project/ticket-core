# Spring Modulith Application Module 경계로 전환한다

## Status

채택·구현됨. 이 ADR은 모듈 경계 메커니즘(단일 Gradle 프로젝트, 패키지 기반 닫힌 모듈, 모듈 간
참조 방식, 이벤트, Flyway 소유권)을 다룬다. **오류 계약은 범위 밖이다** — ADR 0002가 다룬다.

module set과 의존 DAG는 이후 ADR 0005·ADR 0006이 각각 다시 supersede했다. 의존 DAG의 원본은
`com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG`와 `docs/architecture.md`다 — 아래 §3은 이
ADR이 정한 원칙만 남기고 값은 담지 않는다. `catalog`가 `show`로 개명되고 물리 공연장·좌석
(Venue/Seat)이 `venue`로 분리된 것(ADR 0006), §11(showlike 흡수)이 그 뒤 되돌려진 것(ADR 0006
§2)도 최신 값은 아래에서 원본 문서로만 가리킨다.

## 배경

`docs/architecture.md`가 기록하던 계층형 멀티 모듈(`core-api → core-app → core-domain`,
`core-infra`가 양쪽을 구현) 구조는 계층 경계는 강제했지만 업무 기능 경계는 강제하지 않았다.
같은 계층 모듈(`core-app`, `core-domain`) 안에서는 `order`, `hold`, `show`, `auth`가 서로 아무
제약 없이 참조할 수 있었다. Spring Modulith는 반대로 **기능 경계를 닫힌 모듈로 강제**하고 계층은
모듈 내부의 자유로운 배치에 맡긴다. 이 결정은 그 전환을 승인된 최종 형태로 기록한다.

## Current Decision

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
구현(web/application/domain/infrastructure 전부)은 `<module>` 아래에 둔다. 어떤 모듈도
`Type.OPEN`으로 선언하지 않는다. 모듈 루트 아래에 `internal` 계층을 따로 두지 않는다 — Spring
Modulith는 module root의 타입만 공개로, 하위 패키지는 이름과 무관하게 모두 내부로 취급하므로
`internal`은 정보량 없는 폴더 한 층만 반복됐다. 무엇을 어디에 두는지는 `docs/architecture.md`의
"Module Structure"가 원본이다.

### 3. 의존 DAG

각 모듈 `package-info.java`의 `allowedDependencies`가 원본이다. 의존 DAG의 원본은
`com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG`와 `docs/architecture.md`다 — 이 ADR은 값을
싣지 않는다.

`shared`/`error`/`web`은 `@Modulith(sharedModules = ...)`로 선언한다. 업무 의미가 없고 거의 모든
모듈이 참조하는 leaf 계약이라 각 모듈 `allowedDependencies`에 일일이 적지 않고 전역 허용으로
두며, 그 목록에는 업무 모듈 의존만 남긴다. 어느 모듈이 실제로 이 셋을 참조하는지는
`ModularityTests.APPROVED_DEPENDENCY_DAG`가 모듈별로 고정한다.

### 4. 모듈 간 참조는 scalar ID와 공개 API로만

모듈을 넘나드는 JPA 연관관계(`@ManyToOne` 등)와 DB FK는 금지한다. 예: booking이 소유한
`PerformanceSeat`는 `performanceId`/`seatId`를 `long` 컬럼으로만 갖고, show의 `Performance`나
venue의 `Seat` 엔티티를 JPA로 참조하지 않는다. 필요한 조회는 상대 모듈이 공개한 인터페이스
(예: `member.MemberLookup`, `venue.VenueLookup`)를 호출해 얻는다.

공개 API는 두 형태만 허용한다.

- **동기 조회/명령 인터페이스** — 작은 단위로 쪼갠 interface와 그 반환값인 **불변 `record`
  snapshot**. JPA entity, Redis/JWT/Spring Web 타입을 노출하지 않는다. 컬렉션은 defensive
  copy한다.
- **커밋 후 이벤트** — booking이 발행하는 `OrderStarted`/`OrderTerminated`. 아래 5번을 본다.

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

**알려진 운영 리스크(추적 중): `EVENT_PUBLICATION.serialized_event` 컬럼 크기.**
`spring-modulith-events-jpa:2.1.1`의 `JpaEventPublication.serializedEvent`에 `@Lob`이 없어
`VARCHAR(255)`/`VARCHAR2(255 CHAR)`로 매핑된다. `OrderStarted`가 `performanceSeatIds`를 포함해
좌석 여러 개(seed 데이터의 `max_can_hold_count` 기준 4개만 담아도)를 담으면 직렬화 JSON이 이미
255자를 넘을 가능성이 크다 — INSERT가 거부되어 event publication 자체가 실패할 수 있는 실사용
리스크다. 이는 마이그레이션 자체의 결함이 아니라, 이벤트 스키마(사용자가 직접 지정한 필드
구성)와 상위 라이브러리 제약이 만나는 지점이라 전환 작업 중에는 임의로 스키마를 바꾸지
않았다. 실제 다중 좌석 주문 트래픽 앞에 배포하기 전에 별도로 결정해야 한다: (a)
`OrderStarted`/`OrderTerminated`에서 `performanceSeatIds`를 빼고 listener가 DB에서 다시
조회하게 하거나(이미 listener는 payload를 source of truth로 쓰지 않고 재조회한다), (b) 이
실패를 감수하고 모니터링하거나, (c) 다른 방식. 이 항목은 아직 완료가 아니다.

### 6. shared module: 호출 대상 계약만 두는 자리

`com.ticket.shared`는 여러 모듈이 공유할 최소 계약을 위한 자리다. `shared`에 두는 것은 **호출
대상 계약**뿐이다: 둘 이상의 독립 module이 의미 그대로 공유하고, 그 타입을 쓰기 위해 어떤
스타터도 추가하지 않아도 되는 타입. 현재 `UuidSupplier`, `CorsProperties`(CORS 허용 origin
`@ConfigurationProperties` 값 홀더 — 스스로 bean을 등록하지 않고, 등록은 값을 쓰는 member의
`SecurityConfig`가 `@EnableConfigurationProperties`로 한다), `CursorPage`가 있다. REST 응답
봉투 4종은 처음에 여기 있었지만 §10에서 `com.ticket.web`으로 옮겼다 — Jackson·Swagger에
결합된 HTTP 표현 계약이라 "프로토콜 결합이 없는 계약"이라는 이 자리의 기준을 만족하지 않는다.

**왜 bean 등록(`@Configuration`)을 두지 않는가.** 계약은 다른 module이 *불러 쓰는* 것이고
`@Configuration`은 포함하는 것만으로 *적용되는* 것이다. 둘이 한 module에 있으면 `shared`를
참조하는 쪽이 Redisson·Querydsl·Swagger·P6Spy 스택과 그 bean까지 강제로 함께 받아 "가져다 쓸
수 있는 모듈"이 성립하지 않는다. 게다가 `TicketApplication`이 `@Modulith(sharedModules =
"shared")`를 선언하므로 `shared`는 **모든 `@ApplicationModuleTest`에 항상 포함된다** — bean
등록이 여기 있으면 모든 module의 STANDALONE 테스트가 실제 Redis 연결까지 함께 띄우고, 그
배선이 module 테스트의 성패를 좌우한다. 그래서 domain-free 기술 설정 여덟 개
(`SwaggerConfig`/`P6SpyConfig`/`QuerydslConfig`/`RedissonConfig`/`UuidSupplierConfig`/
`EventPublicationMaintenance`/`SchedulingConfig`/`SystemClockConfig`)를 `com.ticket.config`로
옮겼다(§9). 이 규칙은 `com.ticket.shared.SharedModulePurityTest`가 강제한다(`@Configuration`을
일부러 넣어 실패하는 것을 실측 확인).

옮기고 나서 module 테스트 두 개가 실제로 bean을 잃었다 — `CatalogModuleTests`(당시 이름,
`JPAQueryFactory`·`Clock`)와 `BookingModuleTests`(`Clock`)는 `@MockitoBean`으로 대체했다.
STANDALONE wiring smoke test가 진짜 DB·시계를 받지 않는 쪽이 격리에 맞고, 이 대체는 두
테스트가 이미 다른 외부 의존에 쓰던 방식과 같다.

**`shared`에 두지 못한 반례(기록 유지)**: `JpaAuditingConfig`/`SecurityContextAuditorAware`(JPA
auditing이 채우는 감사자 id)는 member의 공개 계약 `AuthenticatedMember`를 참조한다. 처음에는
이것만 예외로 `shared`에 두려 했으나, member가 이미 `shared`(`UuidSupplier`)를 참조하는 것과
맞물려 `member ↔ shared` 순환이 되어 `ApplicationModules.verify()`가 실패했다
(`com.ticket.ModularityTests.verifiesModuleStructure()`에서 실측 확인). `WebConfig`/
`WebSocketConfig`/`HttpServiceConfig`/`JwtConfig`는 애초에 특정 module의 내부를 직접 참조해
domain-free하지도 않다. 이 여섯 개도 `com.ticket.config`가 소유한다(§9).

`shared`에 두지 **않는** 것: business logic, 특정 module에만 의미 있는 동작, bean을 등록하는
코드, 그리고 여러 module의 내부를 동시에 참조해야만 배선되는 설정(§9 `config` 참고) — 마지막
것을 shared에 두면 shared가 사실상 모든 module과 결합돼 "safe to depend on without redeploy
coupling"이라는 존재 이유가 무너진다.

`package-info.java`가 애노테이션 없이 비어 있으면 javac가 `package-info.class`를 만들지 않아
Spring Modulith가 이 패키지 자체를 못 보므로, `@ApplicationModule(displayName = "Shared")`만
선언해 class 없이도 module로 잡히게 했다. `TicketApplication`은 계속
`@Modulith(sharedModules = "shared")`를 선언한다.

### 7. Module-aware Flyway와 물리 데이터 경계

`spring.modulith.runtime.flyway-enabled=true`로 각 추출 모듈이 독립된
`flyway_schema_history_{module}` 이력을 갖는다. 기존 적용 이력(V2~V8, 그리고 H2/Oracle vendor
V3~V7)은 byte-for-byte 그대로 `db/migration/__root`,
`db/migration-vendor/{h2,oracle}/__root`로 옮겨 checksum을 보존했다. 모듈이 소유하는 새 schema
변경(cross-module FK 제거, scalar column 전환 등)은 `db/migration/{module}`,
`db/migration-vendor/{h2,oracle}/{module}`에 module별로 1부터 새로 버전을 매겨 추가한다. 상세
절차는 [`docs/operations.md`](../operations.md)를 따른다.

### 8. bootstrap: 영구 composition-root 예외 자리

`com.ticket.bootstrap`은 `com.ticket.core`/`storage`/`support` 같은 legacy 패키지와 같은
predicate로 Modulith 검증에서 제외되지만, 성격은 다르다. legacy 패키지는 "아직 옮기지 못한
코드"로 다른 작업이 계속 줄이다 이동이 끝나면 사라지는 자리인 반면, `bootstrap`은 **의도적이고
영구적인 네 번째 카테고리**다 — 업무 Application Module, 공유 유틸리티/기술 설정 module
(`shared`, §6), composition-root module(`config`, §9)에 이어 필요할 때 쓰는 예외 자리다.
`config`가 `@NamedInterface`로 좁혀 열 수 없을 만큼 결합이 크거나 임시적인 cross-module 배선이
생기면(예: 여러 module의 내부를 광범위하게 참조해야 하는 임시 조치), Modulith 검증에서 완전히
빠지는 이 자리가 그 도피처로 남는다 — `config`보다 느슨하고 감시가 없는 대신, 남용 시 눈에 잘
띄지 않는다는 trade-off가 있다. 지금은 이 자리를 쓰는 production 코드가 없다.

### 9. config: 전역 배선을 소유하는 composition-root module

`com.ticket.config`는 `shared`(§6)와 반대되는 존재 이유를 갖는다 — `shared`는 다른 module이
**호출하는 계약**만 갖고 bean을 등록하지 않으며, `config`는 정확히 그 반대로 **앱에 적용되는
전역 배선**이 있을 자리다. 어떤 business module도 참조하지 않는 domain-free 전역 기술 설정과,
member의 공개 계약만 쓰는 `JpaAuditingConfig`/`SecurityContextAuditorAware`가 있다. domain-free
설정은 module 결합이 없어 `shared`에 둘 수도 있어 보이지만 bean을 등록한다는 점에서 계약과
성질이 다르고, `sharedModules` 선언 때문에 모든 module 테스트에 함께 뜬다(§6). `bootstrap`(§8)과
달리 이 module은 Modulith 검증에서 제외되지 않는다.

**등록은 소유 module이 한다.** 처음에는 `config`가 `WebConfig`/`WebSocketConfig`/
`HttpServiceConfig`/`JwtConfig`로 member·booking의 물건을 대신 등록해 주면서 여러 module의
내부를 참조했다. 등록할 물건의 주인이 직접 등록하면 그 참조가 아예 필요 없다는 점을 확인하고
넷 다 소유 module로 옮겼다 — `AuthenticatedMemberArgumentResolver`/`JwtProperties`/카카오 HTTP
client 등록은 member가, STOMP 브로커·endpoint는 booking이 각자 `WebMvcConfigurer`/
`WebSocketMessageBrokerConfigurer` 구현으로 한다. 근거는 Spring이 이 인터페이스들을 **여러 개
모아** 순서대로 적용한다는 것이다 — module마다 자기 것을 하나씩 둬도 되고, 새 module이 자기
확장점을 추가할 때 `config`를 고칠 필요가 없다(전역 설정이 module 수만큼 커지지 않는다). 그
결과 `config`의 업무 module 의존은 `member` 하나로 줄었고 `@NamedInterface`는 하나도 남지
않았다.

`config`는 `shared`(`UuidSupplier`)도 참조한다(§6) — module 결합이 없는 shared 참조는 제한
없이 허용된다. `config`를 참조하는 다른 module은 없다(leaf) — composition root는 재사용 가능한
공개 API가 아니라 배선 지점이기 때문이다.

### 10. web module: REST 표현 계약

REST 응답 봉투(`ApiResponse`/`ErrorMessage`/`ResultType`/`SliceResponse`)는 `com.ticket.web`이
소유한다. 처음에는 `shared`에 뒀지만(§6), 이 자리의 기준("프로토콜·프레임워크 결합이 없는
호출 대상 계약")을 만족하지 않는다.

- **`shared`가 아닌 이유**: 봉투는 Jackson·Swagger 애노테이션을 달고 있는 HTTP 표현 계약이고,
  이 앱의 모든 채널이 쓰는 것도 아니다 — booking이 좌석 상태를 발행하는 WebSocket payload는 이
  봉투를 쓰지 않는다. `shared`에 두면 "전 module 공통 유틸리티"처럼 보여 범위가 가려지고, 값
  자체가 외부 API 계약이라 변경 주체가 분명해야 하는데 `shared`는 구조적으로 "공통이니
  아무나"가 된다.
- **`error`가 아닌 이유**: `error`는 오류 계약(code·예외 base·전역 handler)을 소유한다. 성공
  응답 봉투까지 그 module에 두면 이름과 내용이 어긋난다. 방향은 `error -> web` 하나이며, 반대로
  `web`이 오류 타입을 참조하면 순환이 되어 `ApplicationModules.verify()`가 실패한다 —
  `ApiResponse`가 오류 타입을 모른 채 완성된 code·message·data 문자열만 받는 이유는 ADR 0002
  이후 그대로다.
- **`sharedModules`로 선언한다**: `shared`·`error`와 같은 관례다(§3). 각 module의
  `allowedDependencies`에는 업무 module 의존만 남고, 어느 module이 실제로 web을 참조하는지는
  `ModularityTests.APPROVED_DEPENDENCY_DAG`가 고정한다. 이 module에도 bean을 등록하는 코드를
  두지 않는다 — `sharedModules`는 web을 모든 `@ApplicationModuleTest`에 포함시키므로 §6과 같은
  이유가 그대로 적용된다.
- **`internal`이 없다**: 구현이랄 것이 없고 전부 다른 module이 쓰는 공개 계약이라 module root에만
  class가 있다. 각 module의 controller가 사는 `<module>.web`과는 다른 자리다 — 그쪽은 그
  module의 endpoint이고, 이 module은 그 endpoint들이 공유하는 표현 계약이다.

**이 결정으로 바뀌지 않은 것**: 봉투의 JSON 모양(`{result, data, error{code, message, data}}`),
필드 이름, `SliceResponse`의 커서 필드는 그대로다. `ticket-fe`와 `gatling-test`가 이 모양에
의존하므로 package 이동만 하고 타입 구조는 손대지 않았다.

## 검토한 대안

Spring Modulith 전환 시점에 세 가지 접근법을 검토했다.

1. **기존 Gradle 모듈을 유지하고 Spring Modulith만 추가.** 이동량이 적고 기존 컴파일 경계를
   유지한다. 그러나 기능 하나가 계층 모듈 네 개에 흩어지는 핵심 문제를 해결하지 못하고 Gradle
   경계와 Modulith 경계를 동시에 관리해야 한다. 선택하지 않았다.
2. **`core:booking`, `core:catalog` 같은 기능별 Gradle 모듈로 교체.** 기능 응집과 컴파일 격리를
   함께 얻지만, 단일 배포 애플리케이션에 두 종류의 모듈 경계를 중복해 관리하게 된다. 초기 경계
   조정 비용이 크고 독립 배포 필요성이 아직 증명되지 않았다. 선택하지 않았다.
3. **단일 Gradle 애플리케이션 안의 기능별 Spring Modulith 모듈.** Spring Modulith의 기본
   모델과 가장 가깝고 패키지 이동만으로 경계를 조정할 수 있다. 모듈 공개 API, 이벤트, 테스트,
   문서, 관측을 한 모델에서 파생할 수 있다. 이 접근법을 선택했다 — 위 §1~§10이 그 결과다.

## ADR 0001과의 관계

ADR 0001(Selection과 Hold를 독립으로 둔다)의 **업무 결정 자체는 바뀌지 않았다.** Selection이
UX 보조 상태이고 Hold만이 판매 정합성을 지킨다는 원칙은 이 전환 이후에도 그대로 유효하다.
ADR 0001이 예시로 든 구현 클래스는 이 전환으로 이름이 최신화됐을 뿐(예:
`AsyncHoldCreationPostCommitNotifier`가 §5의 `OrderStarted`/`OrderTerminated` 발행과
`BookingEventListeners`로 대체) 그 결정을 뒤집지 않는다.

## ADR 0002와의 관계

오류 계약은 이 ADR의 범위가 아니다. ADR 0002가 모듈 소유 오류 카탈로그를 직접 결정한다.

## ADR 0005·ADR 0006과의 관계

이 ADR의 module 경계 원칙(패키지 기반 닫힌 module, 공개 계약은 작은 interface + 불변 record
snapshot, cross-module JPA 금지, Spring Modulith 이벤트, module-aware Flyway, §6~10의 각
공유/전역 모듈 존재 이유)은 그대로 유효하다. **§3(의존 DAG)의 module set·값과 §11(showlike
흡수)만 이후 ADR 0005·ADR 0006이 순차로 supersede했다** — ADR 0006이 §2가 그 되돌림까지
포함해 최종 상태를 기록한다. 최신 module set·DAG의 원본은 항상 ADR 0006과
`com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG`다.
