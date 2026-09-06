---
name: verify
description: >
  ticket 저장소에서 변경 범위에 맞는 최소 검증을 골라 실행하고 결과를 정확히 보고한다.
  테스트 실행, 빌드 확인, 커밋 전 검증, "이거 돌려봐 / 검증해줘" 요청을 받았을 때 쓴다.
allowed-tools: Bash(./gradlew:*) PowerShell(.\gradlew.bat:*) Bash(rg:*) Bash(git diff:*)
---

# 검증

**전체를 돌리는 것은 기본값이 아니다.** 바꾼 것에 닿는 검증부터 좁게 실행하고, 전체는 push
직전이나 원인을 모를 때 돌린다.

단일 Gradle Spring Boot 프로젝트다. `:core:core-api:test` 같은 subproject 명령은 없다 —
`--tests`로 범위를 좁힌다.

## 무엇을 돌릴지

| 상황 | 실행 |
| --- | --- |
| 컴파일 여부만 빠르게 보고 싶다 | `./gradlew compileJava` |
| 특정 모듈(엔티티, use case, controller, adapter 등)을 고쳤다 | `./gradlew test --tests "com.ticket.<module>.*"` (예: `com.ticket.booking.*`) |
| 모듈 경계, 패키지 위치, `package-info.java`를 건드렸다 | 구조 테스트(아래) |
| Redis adapter, key, TTL, expiration listener를 고쳤다 | `./gradlew test --tests "com.ticket.core.infra.redis.CoreRedisIntegrationTest"` (Docker 필요) |
| 주문·hold·좌석 상태 흐름이나 모듈 간 조립을 고쳤다 | `./gradlew test --tests "com.ticket.bootstrap.*"` (Docker 필요, 전체 컨텍스트·E2E) |
| Modulith 이벤트(발행·리스너·재시도)를 고쳤다 | `./gradlew test --tests "*EventPublication*" --tests "*ScenarioTest"` |
| 특정 테스트만 보고 싶다 | `./gradlew test --tests "com.ticket.booking.application.order.command.*"` |
| 배포 산출물까지 확인한다 | `./gradlew clean bootJar -x test` |
| push·PR 직전 | `./gradlew clean test bootJar`(CI와 같은 명령) |
| 문서만 바꿨다 | `rg -n "찾을_문구"` 와 `git diff --check` |

Windows PowerShell에서는 `.\gradlew.bat`을 쓴다.

## 구조를 건드렸으면 이것부터

```bash
./gradlew test --tests "com.ticket.ModularityTests"
./gradlew test --tests "com.ticket.*.*ModuleTests"
./gradlew test --tests "com.ticket.core.CoreLayerArchitectureTest"
```

`ModularityTests`가 Application Module 경계 전체(닫힌 모듈, 승인된 DAG, cross-module 참조)를
검사하는 본체다. 새 코드의 위치가 의심스러우면 이것부터 돌린다. 무엇을 막는지는
[architecture.md](../../../docs/architecture.md#아키텍처-규칙)를 본다.

각 모듈의 `<Module>ModuleTests`(`AdmissionModuleTests`, `CatalogModuleTests`,
`MemberModuleTests`, `BookingModuleTests`, `PaymentModuleTests`)는
`@ApplicationModuleTest(verifyAutomatically = false)`로 그 모듈이 STANDALONE으로
부트스트랩되는지만 본다. 전체 구조 검증은 여기서 하지 않는다 — `ModularityTests`의 몫이다.

`com.ticket.core` 아래에는 아직 legacy 계층형 ArchUnit 테스트(`CoreLayerArchitectureTest`,
`CoreApiArchitectureTest`, `CoreDomainArchitectureTest`, `CoreInfraArchitectureTest`)도 남아
있다. legacy 코드를 건드렸으면 이것도 함께 돌린다. `com.ticket.bootstrap`은 지금 class가 없어
`BootstrapArchitectureTest`는 지웠다(검사 대상 없는 rule이 실패하는 것을 실측 확인) — 그
패키지에 새 class가 생기면 그때 필요한 규칙을 다시 만든다.

## 통합 테스트와 E2E

Testcontainers를 쓰는 테스트가 있으므로 **Docker가 실행 중이어야 한다.** Docker가 없으면 실패의
원인이 코드가 아니다.

Redis key, TTL, expiration listener, Redisson 변경은 단위 테스트만으로 확인했다고 보지 않는다.

`com.ticket.bootstrap.ApplicationContextLoadTest`와 `com.ticket.bootstrap.booking.*E2ETest`는
실제 서버를 띄우고 HTTP로 예매 흐름을 관통한다. 모듈별 단위 테스트는 각자 mock에 대해 맞으면
통과하므로 **모듈 사이 연결이 깨진 것을 잡지 못한다.** 주문 생성·취소, hold, 좌석 상태 계산,
커밋 후 이벤트 처리를 건드렸으면 여기까지 돌린다.

## Modulith 이벤트 검증

주문 생성/종료 후속 처리를 고쳤으면 `PublishedEvents`/`Scenario` 기반 테스트로 발행-저장
원자성, 재시도, 멱등성을 함께 본다. 무엇을 고정하는지는
[testing.md의 Modulith 이벤트 테스트](../../../docs/testing.md#modulith-이벤트-테스트)를 본다.
`EventPublicationMaintenance`(purge·재제출 주기)를 고쳤으면 정책 값(1분 재제출, batch 100,
동시 4, 재시도 상한 10회, 30일 purge)이 바뀌지 않았는지 함께 확인한다.

## 결과를 보고할 때

- 통과·실패 수를 그대로 적는다. "대부분 통과" 같은 요약은 쓰지 않는다.
- **돌리지 않은 범위를 밝힌다.** "booking 모듈 통과"와 "전체 통과"는 다른 말이다.
- 실패가 이번 변경 때문인지 기존 상태인지 구분한다. 애매하면 변경 전 커밋에서 같은 명령을 돌려
  비교한다.
- Docker 미실행, Gradle 캐시, 작업 디렉터리처럼 코드와 무관한 원인이면 그 사실을 먼저 적는다.
  환경 문제를 코드 결함으로 보고하지 않는다.
- **검증에 실패한 상태로 커밋하지 않는다.** 테스트 스킵과 `--no-verify`는 없다.

새 테스트를 쓰는 관례(한국어 메서드 이름, 무엇을 함께 고정할지)는
[testing.md](../../../docs/testing.md#새-테스트를-추가할-때)를 본다.
