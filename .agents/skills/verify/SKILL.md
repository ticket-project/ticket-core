---
name: verify
description: >
  ticket 저장소에서 변경 범위에 맞는 최소 검증을 골라 실행하고 결과를 정확히 보고한다.
  테스트 실행, 빌드 확인, 커밋 전 검증, "이거 돌려봐 / 검증해줘" 요청을 받았을 때 쓴다.
allowed-tools: Bash(./gradlew:*) PowerShell(.\gradlew.bat:*) Bash(rg:*) Bash(git diff:*) Bash(find:*)
---

# 검증

**전체를 돌리는 것은 기본값이 아니다.** 바꾼 것에 닿는 검증부터 좁게 실행하고, 전체는 push
직전이나 원인을 모를 때 돌린다.

단일 Gradle Spring Boot 프로젝트다(`settings.gradle`에 `rootProject.name` 한 줄뿐). `:core:core-api:test`
같은 subproject 명령은 없다 — `--tests`로 범위를 좁힌다.

Windows PowerShell에서는 `.\gradlew.bat`을 쓴다.

## 변경 범위별 전략

| 변경 범위 | 무엇을 돌리는가 |
| --- | --- |
| 컴파일만 빠르게 확인 | `./gradlew compileJava` |
| 특정 BC/모듈 코드(entity, use case, controller, adapter 등) | 그 package test: `./gradlew test --tests "com.ticket.<module>.*"` (예: `com.ticket.booking.*`) |
| 모듈 경계, 패키지 위치, `package-info.java`, aggregate 연관관계 | 구조 테스트 — [아래](#구조를-건드렸으면-이것부터), 전체 목록은 [testing.md의 "구조 테스트" 표](../../../docs/testing.md#구조-테스트)가 원본 |
| Redis adapter, key, TTL, expiration listener | 그 BC의 Redis integration test(Docker/Testcontainers 필요) |
| Modulith 이벤트(발행·리스너·재시도) | `EventPublicationMaintenance` 관련 scenario test — [아래](#modulith-이벤트-검증) |
| 주문·hold·좌석 상태 흐름이나 모듈 간 조립 | `com.ticket.bootstrap.*`의 관련 E2E(Docker 필요, 전체 컨텍스트) |
| 배포 산출물까지 확인 | `./gradlew clean bootJar -x test` |
| push·PR 직전 | `./gradlew clean test bootJar`(CI의 `.github/workflows/ci.yml`과 같은 명령) |
| 문서만 바꿨다 | `rg -n "찾을_문구"`와 `git diff --check` |

**실제 테스트 클래스는 소스에서 탐색해 고른다 — 위 표는 카테고리이지 클래스 이름이 아니다.**
하드코딩된 FQCN을 그대로 믿지 않는다. 찾는 방법:

```bash
# 특정 BC의 모듈 STANDALONE 테스트
find src/test -iname "*ModuleTests*"

# 구조 테스트(경계·순수성·연관관계)
find src/test -iname "*ArchitectureTest*" -o -iname "*PurityTest*" -o -iname "*AssociationTest*"

# 특정 이름을 아는 클래스의 실제 패키지(패키지를 착각하면 --tests가 조용히 0건 통과한다)
find src/test -iname "<클래스이름>*"

# 통합/E2E 테스트
find src/test -path "*bootstrap*" -iname "*.java"
```

## 구조를 건드렸으면 이것부터

```bash
./gradlew test --tests "com.ticket.ModularityTests"
./gradlew test --tests "com.ticket.*.*ModuleTests"
```

`ModularityTests`가 Application Module 경계 전체(닫힌 모듈, 승인된 DAG, cross-module 참조)를
검사하는 본체다. 새 코드의 위치가 의심스러우면 이것부터 돌린다. 무엇을 막는지는
[architecture.md](../../../docs/architecture.md#아키텍처-규칙)를 본다.

각 모듈의 `<Module>ModuleTests`(`BookingModuleTests`, `ShowModuleTests`, `VenueModuleTests`,
`FavoriteModuleTests`, `MemberModuleTests`, `PaymentModuleTests`)는
`@ApplicationModuleTest(verifyAutomatically = false)`로 그 모듈이 STANDALONE으로
부트스트랩되는지만 본다. 전체 구조 검증은 여기서 하지 않는다 — `ModularityTests`의 몫이다.
`com.ticket.DomainPurityTest`(ArchUnit)는 6개 BC 전부에서 `<bc>.domain`이 다른 BC를 참조하지
않는지 고정하고, `com.ticket.AggregateAssociationTest`는 같은 module 안에서 다른 aggregate를
객체 연관관계로 묶지 않았는지 고정한다.

`com.ticket.bootstrap`은 지금 class가 없어 `BootstrapArchitectureTest`는 지웠다(검사 대상
없는 rule이 실패하는 것을 실측 확인) — 그 패키지에 새 class가 생기면 그때 필요한 규칙을
다시 만든다.

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

## 완료 판정 체크리스트

아래 중 하나라도 해당하면 아직 완료가 아니다. 검증을 실행하기 전에도, 결과를 보고하기 전에도
확인한다.

- `com.ticket.ModularityTests`나 관련 구조 테스트가 실패한다.
- 변경한 흐름에 대응하는 테스트가 없다(새로 필요한 테스트를 작업 범위에서 뺐다).
- Docker 미실행, Gradle 캐시, 작업 디렉터리처럼 **환경 문제를 코드 결함으로 착각**하고 있다 —
  반대로 환경 문제를 이유로 검증 자체를 건너뛰지도 않는다.
- 테스트를 스킵하거나(`@Disabled` 방치, `--tests` 범위를 좁혀 실패를 피함) `--no-verify`로
  훅을 우회했다.
- 실패가 이번 변경 때문인지 기존 상태(pre-existing)인지 구분하지 않고 넘어갔다.
- 이미 적용된 Flyway 파일을 수정해 마이그레이션 테스트를 우회했다.
- 관측 지표나 로그만 보고 정합성을 확인했다고 판단했다(실제 검증 실행 없이).
- 검증에 실패한 상태로 커밋했다.

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
