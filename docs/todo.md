# 남은 작업 (2026-10-25 주석·문서 조사)

2026-10-25 주석·문서 전수 조사에서 찾았지만 문서·주석 수정만으로 끝나지 않아 남긴 항목이다. 코드·설정·테스트를
바꾸거나 정책을 정해야 한다. 미해결 항목의 추적 원본은 [CONTRIBUTING](../CONTRIBUTING.md#이슈와-라벨)대로 GitHub
Issues이므로, 착수하거나 Issue로 옮긴 항목은 여기서 지운다. 각 항목의 줄 번호는 조사 시점(`67d197fae`) 기준이다.

## 코드·설정·테스트 수정

### `architectureTest`가 `ArchitectureRulesTest`의 규칙을 돌리지 않는다

- 사실: `src/test/java/com/ticket/ArchitectureRulesTest.java`에 JUnit `@Tag("architecture")`만 있고 ArchUnit
  `@ArchTag`가 없다. ArchUnit JUnit 엔진은 `@ArchTag`만 읽으므로 `architectureTest`(`includeTags 'architecture'`)에서는
  `@Test` 6개만 돌고 `@ArchTest` 규칙 12개(계층 방향, api 순수성 등)는 돌지 않는다. 2026-10-25에 실행해 6건만 도는 것을
  확인했다. 태그 필터가 없는 전체 `test`와 CI에서는 돈다.
- 영향: AGENTS.md·skill·`build.gradle` 주석이 안내하는 대로 `architectureTest`만 돌리면 계층 규칙이 검증됐다고 믿게 된다.
- 후보: `@ArchTag("architecture")`를 함께 붙이고 `architectureTest` 실행 건수가 늘었는지 확인한다.

### 로컬 compose의 Core가 default 프로파일로 떠서 Flyway가 꺼진다

- 사실: `docker-compose-local.yml`의 `core` 서비스, `Dockerfile`, `application.yml`, `../local-env/ticket-core.env`
  어디에도 `SPRING_PROFILES_ACTIVE`가 없다. default 프로파일은 `spring.flyway.enabled: false`다. 실제 기동으로는
  확인하지 않았다.
- 영향: 새 migration(예: booking V4)이 적용되지 않은 채로 부하를 잴 수 있다. gatling-test `reset-local.ps1`의
  "Core가 기동하며 Flyway로 스키마를 만든다"도 성립하지 않을 수 있다.
- 후보: compose `core` 환경에 `SPRING_PROFILES_ACTIVE: local`을 넣고 기동 로그에서 Flyway 적용을 확인한다.

### 주문 생성이 모든 무결성 위반을 E5004로 바꾼다

- 사실: `CreateOrderUseCase`는 같은 회원·회차 PENDING 유일 인덱스 위반을 E5004(409)로 바꾸려고
  `DataIntegrityViolationException`을 통째로 잡는다.
- 영향: 다른 NOT NULL·길이 위반도 "진행 중인 주문" 409로 응답해 원인을 가린다. 발생 가능성은 낮다.
- 후보: 제약 이름(`uk_orders_pending_member_performance`)으로 걸러 그 경우만 E5004로 바꾼다.

### 회차 시작 시각의 null 계약이 세 곳에서 다르다

- 사실: 스키마에서 `performances.show_id`·`start_time`은 nullable이다. `ShowQuerydslRepository`의 `required()`는
  "NOT NULL 컬럼"이라며 null이면 예외를 던지고, 공개 snapshot(`PerformanceSummarySnapshot`, `PerformanceSaleSnapshot`)은
  시작 시각을 `@Nullable`로 선언한다.
- 후보: 스키마에 NOT NULL을 걸지(migration·운영 데이터 확인), `@Nullable`을 지울지, 단언을 지울지 하나로 정한다.

### 회차 표시값 10분 캐시 사이에 등급이 추가되면 주문이 500이 된다

- 사실: `PerformanceSaleHeaderReader`는 등급 표시값까지 회차별 10분 캐시한다. 그 사이 등급이 추가되면 등급별 잔여석
  응답에서 빠지고, 그 등급 좌석의 주문은 `PendingOrderCreator`가 등급 표시값을 찾지 못해 `IllegalStateException`(500)이 된다.
- 영향: 지금은 등급을 바꾸는 관리 기능이 없어 낮다. 관리 기능을 만들 때 캐시 무효화를 함께 정한다.

### seed 실패 출력이 일부 작업에서 사실과 다르다

- 사실: `SeedRunner`는 실패한 작업에 "이 작업의 트랜잭션은 롤백됐습니다", "실패(롤백)"을, `SeedProgram`은 앞 작업이
  모두 건너뜀이면 "커밋된 작업은 없습니다. DB 상태는 실행 전과 같습니다."를 출력한다. 배경 주문(`BackgroundOrderSeeder`)은
  회차마다 커밋하고 identity 동기화(`PostgreSqlIdentitySynchronizer`)는 트랜잭션 없이 돌므로, 이 두 작업이 중간에
  실패하면 일부가 이미 반영돼 있다.
- 후보: 작업이 자기 트랜잭션 성격을 알리게 하고 출력 문구를 그에 맞춘다.

### 테스트가 이름이나 주석이 말하는 것을 검증하지 않는다

- `seed/src/test/java/com/ticket/seed/CuratedSeedStatementsTest.java`의 `회차좌석_시드는_연속_RESERVED_블록을_만든다`:
  비율 30~60%·2~4연석 assertion이 테스트 안의 옛 공식(`blockSize`, `cycleSize`)만 계산한다. 지금 SQL은 해시 `MOD`
  방식이라 블록 구조가 없다. 실제 SQL을 적재해 분포를 재거나, 이름과 메시지를 실제 검증 범위로 줄인다.
- `src/test/java/com/ticket/member/persistence/MemberSocialAccountPersistenceTest.java`: orphanRemoval·CascadeType.REMOVE가
  붙으면 실패한다고 주석에 적었지만, 탈퇴는 soft delete라 두 설정이 붙어도 통과한다. 실제 방어는
  `MemberSocialAccountMappingTest`다. 주석을 고치거나 실제로 잡는 사례를 만든다.
- `SeatSelectionWriterTest`의 이름 일부("락 내부", "DB검증 후", "락 획득 시점")는 assertion이 직접 확인하지 않는다.

### 쓰이지 않는 테스트 대역과 메서드

- `LikeModuleTests`·`ShowModuleTests`의 `MemberLookupApi` mock은 쓰는 bean이 없다. booking·like·venue 모듈 테스트의
  `JPAQueryFactory` mock도 해당 모듈 production 코드가 쓰지 않는다.
- `RecordingDistributedLock`의 `failWith()`·`acquisitions()`, `PerformanceRepository.findPerformanceGrades`는 production·테스트
  호출처가 없거나 테스트만 쓴다.

### 이슈 템플릿이 꺼진 Discussions로 안내한다

- 사실: `.github/ISSUE_TEMPLATE/config.yml`이 Discussions 링크를 두지만 저장소는 `has_discussions: false`다.
- 후보: 링크를 지우거나 Discussions를 켠다.

## 정해야 할 것

### 측정 사다리 상한과 확정 규칙

[Core 수용량](core-capacity.md#측정-순서) 5단계는 50 users/sec까지 오르고 확정 규칙이 없다.
core-capacity skill(`.agents/skills/core-capacity/SKILL.md`)은 100 users/sec까지 오르고 "C를 새 회차로 두 번 더 재서
세 번 모두 PASS면 확정"한다. 2026-10-25 운영 측정은 60 users/sec까지 쟀다. 원본(core-capacity.md)을 어느 쪽으로 정할지
결정한 뒤 두 문서를 맞춘다.

### 운영 `DD_SERVICE` 값

[운영](operations.md#core-용량-관측)은 `ticket-core`, core-capacity skill의 운영 측정은 `ticket-be`로 적는다. 운영 서버
compose의 실제 값을 확인해 한 값으로 맞춘다. 로컬 캐시의 `cache_*` 지표가 `/actuator/prometheus`에 나오는지도 이때
확인한다.

### 주석의 `ponytail:` 표식

`GetOccupiedSeatsUseCase`, `GetSaleOpeningSoonShowsPageUseCase`, `RedissonHoldStore`에 미뤄 둔 작업 표식이 있지만 표기
규칙이 문서에 없다. Issue로 옮길지, 규칙을 문서화할지 정한다.

## 다른 저장소

- gatling-test의 capacity 스크립트 README(`../gatling-test/scripts/capacity/README.md`)는 03 시나리오를 다섯 요청으로, 조회 응답에 공연 요약·주문 조회를 넣어
  적고 있다. Core 쪽 [Core 수용량](core-capacity.md#왜-userssec인가)은 세 요청이다.
