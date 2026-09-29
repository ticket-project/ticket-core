# Ticket Core Backend Agent Guide

이 저장소에서 작업하는 AI 에이전트의 공통 지침이다. 문서와 응답은 한국어, 파일은 UTF-8(BOM 제외)로 작성한다.

## Mission

요청 범위에 집중하는 백엔드 코드 어시스턴트로 일한다. 단일 애플리케이션의 modular-monolith 구조, 기존 Java/Spring 스타일, 공개 API 계약과 저장소·운영 경계를 보존한다.
기본 행동은 좁게 읽기, 기능 소유권에 맞춰 변경하기, 변경에 직접 닿는 검증부터 실행하기다. 관련 없는 리팩터링과 생성물·전체 저장소 탐색으로 범위를 넓히지 않는다.

## Source-Of-Truth Boundary

- `ticket-core`는 인증·공연·좌석 선택/선점·주문 등 백엔드 소스, API 계약, 테스트, Flyway migration, 로컬 실행·운영 문서, CI/배포 workflow와 `seed/`를 소유한다. 업무별 책임은 [아키텍처](docs/architecture.md), 현재 예매 동작은 [예매 수명주기](docs/core-booking-lifecycle.md)가 기준이다.
- `ticket-queue`는 대기열 상태, 입장 처리와 admission token 발급을 소유한다. Core는 회차의 예매 진입 정책과 admission token 검증을 소유한다. 양쪽 설정·연동 경계는 [운영](docs/operations.md#admission-token-검증)을 확인한다.
- `gatling-test`는 부하 시나리오, feeder, 실행 옵션·분산 실행과 리포트의 원본이다. 실행은 형제 저장소 `../gatling-test`에서 하며, Core 쪽 준비·판정 기준은 [부하 검증](docs/testing.md#core-부하-검증)을 따른다.
- 운영 절차는 [operations.md](docs/operations.md), 자동화는 [배포 workflow](.github/workflows/deploy.yml)가 원본이다. 서버의 `docker-compose.yml`은 저장소 밖에 있다. 실제 서버 구성·비밀값·migration 적용 여부를 저장소 설정만으로 단정하지 않는다.

## Minimal Read Order

1. 이 `AGENTS.md`를 읽고 현재 브랜치와 사용자 변경을 확인한다.
2. [README.md](README.md)로 제품·로컬 실행을, [CONTRIBUTING.md](CONTRIBUTING.md)로 커밋·PR·이슈·코드 리뷰 절차를 확인한다.
3. 아래에서 작업에 해당하는 핵심 문서와 관련 [ADR](docs/adr/README.md)만 읽는다. ADR의 채택·대체·미적용 상태를 확인한다.
4. 가장 작은 관련 소스·테스트·migration·API 계약과 필요한 설정을 읽는다. 원인이나 영향 범위가 확인될 때만 탐색을 넓힌다.

| 작업 | 핵심 문서 |
| --- | --- |
| 모듈 경계·코드 배치 | [아키텍처](docs/architecture.md) |
| 이름·코드 작성·용어 | [코드 작성 기준](docs/coding-guidelines.md), [용어집](docs/glossary.md) |
| 예매 상태·선택·선점·실패 처리 | [예매 수명주기](docs/core-booking-lifecycle.md) |
| 테스트 선택·결과 보고·부하 실행 | [테스트 기준](docs/testing.md) |
| DB migration | [migration 규칙](src/main/resources/db/README.md) |
| 초기 데이터 적재 | [seed](seed/README.md) |
| 배포·프로파일·Redis 전환·관측 | [운영](docs/operations.md) |

## Durable Repo Facts

- Java 25 toolchain의 단일 Gradle Spring Boot 프로젝트다. Gradle subproject는 없고 경계는 Spring Modulith Application Module로 관리한다([build.gradle](build.gradle), [settings.gradle](settings.gradle)).
- 빌드는 Gradle wrapper를 쓴다. wrapper 버전은 [wrapper 설정](gradle/wrapper/gradle-wrapper.properties), Spring Boot·Modulith와 도구 버전은 [version catalog](gradle/libs.versions.toml)가 원본이다.
- RDB는 local/dev의 H2 file DB와 prod의 Oracle이며 Redis를 함께 쓴다. Flyway가 스키마를 만들고 Hibernate는 `validate`한다. 프로파일별 실제 값은 `src/main/resources/application*.yml`을 확인한다.
- 서비스 테스트는 `src/test`의 JUnit Platform 기반 단위·Spring/Modulith·ArchUnit·Testcontainers 테스트다. 별도 `integrationTest` source set은 없고, `seed/src/test`는 [별도 seedTest](gradle/seed.gradle)이며 `test`에 포함되지 않는다.
- 포맷은 Spotless와 Palantir Java Format, production null 계약 검사는 Error Prone + NullAway다. 검사 대상·옵션은 [build.gradle](build.gradle)이 원본이다.

## Architecture Rules

1. `com.ticket`의 직접 하위 패키지를 닫힌 Application Module로 유지한다. `Type.OPEN`과 순환 의존을 도입하지 않는다. `allowedDependencies`는 named interface 단위로 명시하고, 모듈 집합·승인 DAG는 `ModularityTests`와 대조한다.
2. 모듈 간 조회·명령은 `<module>.api`의 `@NamedInterface("api")` 공개 계약만 사용한다. 다른 모듈 내부·Repository·JPA entity를 직접 참조하거나 공개 계약에 JPA/Redis/JWT/Spring Web 타입을 노출하지 않는다. `booking.OrderStarted`·`booking.OrderTerminated`는 DB publication에 FQCN이 저장되어 root에 유지한다.
3. 다른 Aggregate는 scalar ID로 참조한다. 모듈 간 JPA 연관관계와 DB FK를 만들지 않고, 다른 BC 데이터는 공개 API로 조합한다. domain은 다른 BC를 참조하지 않는다.
4. HTTP는 endpoint, 조립·트랜잭션은 usecase, 업무 규칙·저장 계약은 domain, 저장 구현은 persistence가 맡는다. domain·usecase·port·module api는 Spring Web/Swagger에 의존하지 않는다. usecase의 자기 모듈 조회 Repository 직접 호출과 일부 응답 record의 Jackson 표기는 현행 허용 범위다.
5. 업무 로직은 소유 모듈에 둔다. `shared`·`security`는 기술 모듈이며 `shared`에 업무 의존을 넣지 않는다. `common`/`util`/`helper`처럼 소유권 없는 패키지를 만들지 않는다. 공개 shared 계약과 실행 배선의 배치는 [아키텍처](docs/architecture.md#module-structure)를 따른다.
6. DB 스키마의 원본은 module 소유 Flyway migration과 H2/Oracle 방언 migration이다. entity 변경만으로 스키마가 바뀐다고 가정하지 않는다. 스키마 변경은 migration·매핑·관련 테스트와 운영 전환 조건을 함께 검토한다([ADR 0020](docs/adr/0020-db-schema-source-of-truth-is-migration.md)).

상세·예외는 [아키텍처](docs/architecture.md), 이름·null 계약·조회 구현은 [코드 작성 기준](docs/coding-guidelines.md)을 따른다. 이름 정리만을 이유로 JSON·HTTP 상태·오류 코드 등 공개 계약을 바꾸지 않는다.

## Verification

변경에 직접 닿는 테스트·포맷 검사부터 작업 중 실행하고 위험에 따라 넓힌다. 작은 변경마다 전체 빌드·테스트를 강제하지 않는다. 선택·확대·결과 보고의 상세 기준은 [testing.md](docs/testing.md#테스트를-돌리는-시점)를 따른다.
아래는 Bash 기준이며 Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`을 쓴다. `--tests` 대상은 실제 소스에서 찾고 실행 건수도 확인한다.

| 변경 범위 | 먼저 할 검증 |
| --- | --- |
| 특정 Java 클래스·기능(booking 예시) | `./gradlew spotlessJavaCheck test --tests 'com.ticket.booking.order.usecase.StartBookingUseCaseTest'`; 관련 모듈로 넓힐 때 `--tests 'com.ticket.booking.*'` |
| 모듈·계층·Aggregate 경계 | `./gradlew architectureTest`와 관련 모듈 테스트; 모듈 간 빈 이동은 컨텍스트 기동 테스트도 실행 |
| Redis·주문/hold·이벤트·migration | 관련 통합·Scenario·E2E·H2/Oracle 테스트까지 확대([선택 기준](docs/testing.md#변경별-검증)); Testcontainers는 Docker 필요 |
| seed | `./gradlew seedTest`; 산출물 격리는 필요시 `./gradlew verifySeedNotInBootJar` |
| 문서만 변경 | `bash scripts/check-docs.sh`, 변경 링크·anchor·명령 근거 확인, `git diff --check` |

공통 설정·의존성·여러 모듈 영향이나 원인 불명 회귀는 전체 검증으로 넓힌다. CI·배포 산출물 확인 명령은 [testing.md](docs/testing.md#변경별-검증)와 [CI workflow](.github/workflows/ci.yml)를 따른다. 필요한 검증이 실패하면 원인을 해결하고 관련 범위를 다시 확인한다.

## Git / Commit / PR

- 기존 사용자 변경을 임의로 되돌리지 않고 요청 범위 밖 변경을 섞지 않는다. 커밋은 요청을 기다리지 않고 목적 하나마다 작게 만들며, 필요한 검증을 완료한 변경만 담는다.
- 제목은 `<type>(<scope>): <한국어 설명>`, 본문은 배경·선택 이유·계약/동작 변화·실제 검증·미실행 범위를 기록한다. type·scope와 PR·이슈 절차는 [CONTRIBUTING.md](CONTRIBUTING.md)를 따른다.
- push·PR·배포는 명시적인 요청이 있을 때만 한다. `master` push 전 [배포 workflow](.github/workflows/deploy.yml)의 trigger·`paths-ignore` 조건을 확인한다.

## Reliability

- 읽지 못한 문서·코드·설정은 추측하지 않고, 실행하지 않은 검증은 통과했다고 보고하지 않는다. 실제 명령·결과·실패·미실행 범위와 환경 제약을 구분한다.
- 현재 구현, 합의된 정책, 미적용 결정과 제안을 구분한다. 코드·테스트와 문서가 다르면 정책을 자동으로 바꾸지 않고 근거와 함께 불일치를 드러낸다.
