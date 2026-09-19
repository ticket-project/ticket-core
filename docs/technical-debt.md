# 기술 부채

아래 항목은 구조적으로 아직 해결하지 않았고, 현재 경계와 권장 방향만 기록한다.

대부분은 **설계** 부채(코드/구조 문제, 결정 없이도 손댈 수 있다)지만 일부는 **제품 결정**이
먼저 필요한 사안이다(코드 문제가 아니라 정책을 정해야 풀린다). "성격" 열로 구분한다.

| ID | 성격 | 현재 위치 | 현재 구조 | 왜 기술 부채인가 | 권장 해결 방향 | 보류 이유 | 호환성 주의 | 완료 조건 | 관련 코드 |
|---|---|---|---|---|---|---|---|---|---|
| TD-03 | 설계 | `show.domain.show` | 도메인이 HTTP 이미지 경로를 만든다 | 표현 계층 규칙이 domain에 유입된다 | image URL 변환을 API/infra adapter로 이동 | 응답 호환성 영향 분석 필요 | 기존 image JSON 유지 | domain이 경로 문자열을 생성하지 않음 | `ShowCardImagePathConverter` |
| TD-04 | 설계 | 각 module의 `endpoint`[^td04] | app UseCase Output/View가 API 응답 타입으로 직접 노출된다 | API와 app 변경이 강하게 결합된다 | API response DTO로 변환 | 전 endpoint 계약 검토 필요 | JSON 필드/상태 유지 | controller 반환 타입이 API DTO | 각 `*Controller` |
| TD-08 | 설계 | `booking.salespolicy.domain` | `QueueMode`, `QueueLevel` 명명이 책임을 충분히 설명하지 않는다 | 정책 의미와 실행 단계가 혼동될 수 있다 | 정책/단계 용어를 ADR로 확정 후 rename | API/claim 영향 검토 필요 | token claim 유지 | 의미와 wire mapping 고정 | `QueueMode`, `QueueLevel` |
| TD-09 | 설계 | `booking.admission` | admission token 책임을 설정/검증 관점으로 함께 표현한다 | 설정과 검증 책임이 분리되지 않는다 | token settings와 guard의 경계를 문서화 | 기존 구성키 유지 필요 | JWT claim/TTL 유지 | 책임별 테스트와 문서 일치 | `AdmissionTokenSettings`, `JwtAdmissionVerifier`(구 `JwtAdmissionGuard`) |
| TD-13 | 제품 결정 | `show.domain.show` | `Show.viewCount`를 증가시키는 코드가 없는데 `POPULAR` 정렬·커서 키로 쓰인다 | 제품 결정이 필요한 사안(비동기 증가 도입 or 정렬 폐기) | 상세 조회 시 비동기 증가, 또는 `POPULAR` 정렬 폐기 | 제품 결정 사안, 코드 문제 아님 | 정렬 API 계약 영향 분석 필요 | 결정 후 반영 | `Show.viewCount`, `ShowSort.POPULAR` |
| TD-14 | 설계 | `venue.domain` | `Venue.gapX`/`gapY`(좌석 간격)를 읽는 코드가 없다 | 죽은 컬럼일 가능성 | 소비자 없음을 재확인 후 컬럼 제거 여부 결정 | 컬럼 drop은 운영 migration이라 신중히 별도 결정 | 응답 영향 없음(비노출 필드) | 컬럼 제거 또는 실제 소비자 확인 | `Venue`, `VenueSummary.SeatMapLayout` |
| TD-15 | 설계 | `show.domain.performance` | "판매 오픈 전에만 가격을 바꿀 수 있다"는 `PerformanceGrade`의 불변식인데, 그 판단 근거(접수 시각·좌석 편성 여부)가 Booking BC에 있다 | show가 혼자 판정할 수 없고 `show -> booking`은 순환이라 금지다. 지금은 가격 변경 메서드 자체가 없어 드러나지 않을 뿐 강제되는 규칙이 아니다 | (A) 잠금 기준을 "좌석 편성"으로 바꾸고 `CreatePerformanceSeatsUseCase`가 show의 공개 command API로 잠금을 알린다(`booking -> show`라 순환 없고 snapshot 시점과 일치) / (B) booking이 편성 시 단가 불일치를 사후 감지 / (C) 문서 규칙으로만 유지 | 가격 변경 기능이 아직 없어 실제로 깨지지 않는다 — 관리자 CRUD 착수 시점에 결정한다 | 가격 snapshot 체인(`PerformanceGrade.price` -> `PerformanceSeat.unitPrice` -> `OrderSeat.unitPrice`) 의미 보존 | 잠금 주체·시점을 결정하고 테스트로 고정 | `PerformanceGrade`, `CreatePerformanceSeatsUseCase`, `PerformanceSalesPolicy` |
| TD-17 | 설계 | `booking.selection` | 회원의 전체 선택 해제가 회차 전체 선택 목록을 읽고 좌석마다 Redis를 호출한다 | 좌석 수에 비례한 Redis 왕복과 좌석 락이 생긴다 | 회원별 선택 인덱스 도입 또는 배치 해제 | **측정하지 않았다.** 대표 규모(회차 600석, 한 회원의 동시 선택은 회차 Hold 한도 이하)에서는 한 자릿수 좌석이라 인덱스를 추가하지 않았다. 인덱스를 두면 선택·해제·TTL 만료 세 경로에서 인덱스와 좌석 키의 정합성을 따로 맞춰야 한다 | Redis key·TTL 의미 유지 | `/loadtest`로 요청 수와 지연을 측정한 뒤 도입 여부 결정 | `DeselectAllSeatsUseCase`, `RedissonSeatSelectionStore.releaseAllByMember` |

**해소된 항목**:

- TD-01(OAuth provider 원본 attributes의 application 유입)은 provider별 해석을
  `OAuth2UserInfoMapper`로 격리하고 정규화된 소셜 신원만 application에 전달하도록 변경했다(그 값 타입은 지금 `member.api.SocialIdentity`다).
- TD-02(회원 탈퇴 application의 Kakao 구현 직접 의존)는 `SocialAccountUnlinker` 포트를 도입하고
  provider별 외부 API 처리를 구현으로 감쌌다. 지금 그 포트와 구현은 `security.oauth`에 있다.
- TD-10(`<module>.domain.**.command`에 정책·값 객체·서비스가 혼재)은 패키지를 모듈 → 계층으로
  평탄화하면서 `command`/`model`/`query`/`store` 하위 패키지 자체가 사라져 전제가 없어졌다.
- 다음은 이번 재편·결함 수정으로 함께 해소됐다. 이벤트 publication `serialized_event` 길이 초과
  (root V9), 커밋 후 리스너가 Redis·WebSocket 작업 중 DB 트랜잭션을 쥐고 있던 문제, 선점 해제
  완료 기록이 발행 실패로 롤백되던 문제, hold 생성 부분 실패의 유령 점유, 오래된 선택 해제 알림,
  만료 배치가 실패 항목 앞에서 멈추던 문제, `HoldPolicy`의 1초 미만 절삭. 근거와 회귀 테스트는
  각 커밋 본문과 `docs/core-booking-lifecycle.md`에 있다.
- TD-16(legacy `core.infra.support` 테스트 패키지)은 여러 모듈이 공유하는 실제 사용처에 맞춰
  `com.ticket.testsupport.persistence`로 이동했다.
- TD-06(`AvailablePerformanceSeatReader` 사용 여부 불명확)은 main·test·seed·SQL·설정과 빈 이름
  문자열까지 확인해 자기 테스트 외 사용처가 없음을 확정하고 제거했다. 이 클래스만 쓰던
  `PerformanceSeatRepository.findAllByStateEquals`와 그 adapter·Spring Data 구현도 함께 지웠다.

해소된 ID는 재사용하지 않는다.

[^td04]: 컨트롤러가 booking/show/member 각 module의 `endpoint`로 옮겨지며
문제의 소재도 함께 옮겨졌다 — module 경계와는 무관하게 여전히 유효한 항목이다.

## 제품 정책 결정 대기

TD 표와 달리 코드 문제가 아니라 **제품 결정이 없어 손대지 않은** 것들이다. 결정 없이 되살리거나
바꾸지 않는다.

| ID | 항목 | 왜 결정이 필요한가 | 관련 코드 |
|---|---|---|---|
| PD-01 | 비밀번호 복잡도 정책(길이·영문·숫자·특수문자) 도입 여부 | 도입하면 기존 회원의 로그인·가입 흐름에 영향이 있다 | `RawPassword` |
| PD-02 | `Email`의 `null` → 빈 문자열 변환 | OAuth2 흐름과 기존 데이터를 조사하기 전에는 바꾸지 않는다 | `Email` |
| PD-03 | `size` 파라미터의 최대값 불일치 | `GetShowsUseCase`(API 문서에 최대 100)와 `GetMyShowLikesUseCase`(`MAX_SIZE` 100) 외에는 근거가 없어 양수 조건만 적용했다. 검색과 판매 오픈 예정 목록의 상한은 결정이 필요하다 | `GetShowsUseCase`, `GetMyShowLikesUseCase` |
| PD-04 | `category` 파라미터의 필수 여부 불일치 | Swagger는 `required = true`지만 조회 구현은 필터로 다룬다. 필수 여부 결정이 필요하다 | `GetLatestShowsUseCase`, `GetSaleOpeningSoonShowsUseCase` |
| PD-05 | path·query 타입 불일치 시 500 응답(예: `/api/v1/shows/abc`) | Bean Validation 이전 단계인 binding 실패라 이번 범위 밖이며, 400으로 바꾸려면 공개 status 변경 결정이 필요하다 | — |

## 보류 원칙

위 항목은 구현하지 않는다. 특히 외부 JSON, HTTP 상태, DB schema, Redis key, JWT claim, WebSocket destination을 바꾸지 않고 해결할 수 있는 설계와 단계별 호환성 계획을 먼저 확정한다.

## 과설계 정리 후보 (OE)

TD·PD와 성격이 다르다. 결정을 기다리는 항목이 아니라 **지울 수 있는데 아직 안 지운 것**이다.
외부 계약(JSON·HTTP 상태·DB schema·Redis key·JWT claim)을 건드리지 않고 내부 구조만 줄인다.

측정 기준: `refactor/jspecify` (2026-09-18, OE-04 적용 후 재측정), `src/main/java` 499파일
19,735줄. `.worktrees/` 제외.

| ID | 태그 | 대상 | 지금 구조 | 대체 | 절감 |
|---|---|---|---|---|---|
| OE-01 | yagni | `*/domain/*Repository.java`, `*/persistence/*RepositoryAdapter.java`, `SpringData*JpaRepository` | 저장소 하나에 타입 3개 ×13벌. Adapter는 1:1 위임이다 — `LikeRepositoryAdapter`는 5개 메서드 중 4개가 시그니처까지 그대로 전달한다 | Spring Data 인터페이스가 이미 포트다. `SpringData*JpaRepository`만 남긴다 | −724줄, 파일 −26 |
| OE-02 | yagni | `*/exception/**` | 오류 코드 1개당 예외 클래스 1개(31개 — `TicketException`과 module 공통 base 4개 포함). `*ErrorCode` enum이 이미 가진 목록을 클래스 이름으로 한 번, `sealed ... permits` 목록으로 또 한 번 적는다 | HTTP 상태를 `ErrorCode` enum에 올리고 module당 예외 1개 + 진단 필드 | −500줄 |
| OE-03 | native | `*ControllerDocs` 전부 | 인터페이스 14개(`*/endpoint/docs/**`에 12개, 나머지 2개는 계층 package를 쓰지 않는 `security.auth`에 flat), 각각 구현 1개, 합계 1,176줄 | 애너테이션을 controller 메서드에 직접 붙인다 | 순 −300줄, 파일 −14 |
| ~~OE-04~~ | yagni | `*/query/*QueryPort.java` | **완료(2026-09-18).** local 조회 14쌍의 `*QueryPort`와 `Querydsl*QueryAdapter`를 `query` package의 구체 `*Query`로 합쳤고, venue의 위임 service 2개는 `*Query`가 공개 API를 직접 구현하며 사라졌다 | — | 실측 파일 −16 |
| ~~OE-05~~ | shrink | `*/domain/*AuditedEntity.java` | **완료(2026-09-19).** 6벌을 `shared.jpa.AuditedEntity` 1개로 합쳤고 entity 21개가 직접 상속한다. `shared.api`(JPA 참조 금지)·`shared.persistence`(역할 이름이라 `..domain..`이 못 참조)를 둘 다 쓸 수 없어 네 번째 named interface `shared :: jpa`를 열었다. `venue`·`payment`가 처음으로 의존(`shared`)을 갖는다. 배경은 [ADR 0018](adr/0018-audit-base-entity-lives-in-shared.md) | 실측 파일 −5 |
| OE-06 | native | `build.gradle`, `shared/config/P6SpyConfig.java` | SQL 파라미터 로깅에 p6spy 의존성 + 47줄 설정 | Hibernate 자체 `org.hibernate.orm.jdbc.bind=TRACE` | −47줄, 의존성 −1 |
| OE-07 | delete | `build.gradle`의 `dumpEpArgs` task | 참조 0개인 디버그 task. 함께 적혀 있던 Spring Initializr 기본 생성물 HELP.md는 이미 지워졌다 | 없음 | −20줄 |
| OE-08 | stdlib | `security/token/UuidSupplier.java`, `UuidSupplierConfig.java` | 테스트 고정을 위해 `@FunctionalInterface` + `@Bean`을 직접 만들었다 | `java.util.function.Supplier<UUID>` | −30줄, 파일 −2 |
| ~~OE-09~~ | shrink | `booking/hold/domain/HoldKeyGenerator.java`, `booking/order/domain/OrderKeyGenerator.java` | **완료(2026-09-19).** 공통 클래스로 합치지 않고 **각 소유 클래스의 private 메서드로 흡수**했다 — `PendingOrderCreator.generateOrderKey()`, `HoldManager.generateHoldKey()`. 호출자가 하나뿐이라 타입을 하나로 줄이는 것보다 없애는 쪽이 짧았다. 키 형식(`ORDER-`/`HOLD-` + 하이픈 없는 UUID)은 그대로다 | — | 실측 파일 −2 |
| OE-10 | native | `build.gradle` | `spring-context`·`spring-tx`·`spring-core`·`spring-beans`·`slf4j-api`·`spring-data-jpa`·`jakarta.persistence-api`를 명시 선언 | starter가 전이로 가져온다. 선언만 지운다(산출물 변화 없음) | −7줄 |
| ~~OE-11~~ | yagni | `*/query/*Query.java` | **완료(2026-09-18).** local 조회 구현 14개를 module별 `persistence`의 조회 Repository 6개(`ShowQueryRepository`, `PerformanceQueryRepository`, `VenueQueryRepository`, `PerformanceSeatQueryRepository`, `OrderQueryRepository`, `LikeQueryRepository`)로 합쳤다. 그 뒤 주문 상세·상태가 `OrderRepository`의 `@Query`로 옮겨가면서 `OrderQueryRepository`는 사라져 지금은 5개다(2026-09-19). 정렬·커서·판매 상태 helper 3개는 `ShowQueryRepository`의 private 메서드로 흡수됐고, `RegionVenueIds`와 `SeatStateSnapshotRow`는 사라졌다(`venue.query`·`like.query` package도 함께). `query`에는 읽기 모델만 남는다 | — | 실측 최상위 타입 −13(404→391), Spring bean −11(152→141), main 파일 −14(499→485), −189줄 |

남은 항목 합계 약 −1,630줄(main의 8%, OE-05·OE-09 완료분 제외), 의존성 −1.
OE-04·OE-05·OE-09·OE-11은 완료했다.

OE-04를 정리하며 세운 기준은 `docs/architecture.md`의 "Repository와 Query"와
`docs/readability-guidelines.md` §3이 원본이다 — 자기 module DB 조회에는 1:1 port/adapter를
두지 않고, interface는 실제 계약·교체 지점·외부 시스템 경계·domain 보호에만 둔다. OE-11로 조회
구현이 `query`에서 `persistence`의 `*QueryRepository`로 옮겨지면서 그 기준 문서(`architecture.md`의
"Repository와 조회 Repository", `code-conventions.md`, `readability-guidelines.md`, `testing.md`)도
함께 갱신했고, 배경은 [ADR 0017](adr/0017-query-implementations-live-in-persistence.md)이다. 나머지 OE
항목(OE-01 Repository 제거, OE-02 예외 통합, OE-03 ControllerDocs 제거)은 이 작업 범위가 아니었고
그대로 남아 있다. OE-05(공통 기반 클래스)는 그 뒤 ADR 0018로 따로 처리했다.

**보류 — 지우지 않는다**: `payment` module(9파일 328줄)은 module 밖 호출자가 없지만 죽은 코드가
아니다. `db/migration-vendor/{h2,oracle}/payment/V1__create_payments.sql`이 실제 `payments` 테이블을
만들고, `PaymentModuleSlicingSchemaTest`·`PaymentTest`·`ArchitectureRulesTest`가 이 module을 검증한다.
결제 기능 착수 전 선행 구축이다. 제거는 TD-14(`Venue.gapX/gapY`)와 같은 운영 migration 결정이라
여기서 다루지 않는다.

**확인했지만 후보가 아닌 것**: `package-info.java` 95개(`@NullMarked`와 modulith `ApplicationModule`
선언이 실제로 걸려 있다), `ShowCursorCodec`(이미 stdlib `Base64`), `testsupport`의 base 클래스들,
실제 설정을 담은 `@Configuration`들.

목록·검색·오픈예정의 `*Row` 6개와 booking seat의 `PerformanceSeatMapRow`·`PerformanceSeatStateRow`는
2026-09-19에 제거했다(조회가 엔티티를 반환하고 use case가 응답을 만든다). 그 대가로 목록 전건에
`Show.info` CLOB이, 좌석 상태 조회에 회차 전 좌석 엔티티가 실린다 — 방침상 수용한 비용이고, 지연
로딩·캐시 도입은 별도 결정으로 남긴다.

**측정이 말하는 것**: main의 인터페이스 68개 중 구현이 2개 이상인 것은 0개다. 10줄 미만 파일이
83개다(OE-04 전에는 각각 82개·93개였다 — 조회 port 14개가 사라진 만큼 줄었다). 위 항목을 다 적용해도 이 비율 자체는 남는다 — 다음 module 추출 때 타입을 먼저 만들지 말라는
신호로 읽는다.
