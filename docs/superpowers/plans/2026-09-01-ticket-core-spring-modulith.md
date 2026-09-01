# Ticket Core Spring Modulith-first 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 현재 계층별 Gradle 멀티프로젝트를 단일 Spring Boot 프로젝트와 `booking`, `catalog`, `identity`, `admission`, `showlike`, `metadata`, `shared` Spring Modulith Application Module로 전환하면서 기존 Selection·Hold·Order 불변식과 운영 동작을 보존한다.

**Architecture:** `com.ticket`의 직접 하위 패키지를 닫힌 Application Module로 사용한다. 모듈 루트에는 다른 모듈이 사용할 동기 API·불변 snapshot·이벤트만 두고, web/application/domain/infrastructure 구현은 모두 `internal` 아래에 둔다. 모듈 간 명령·조회는 공개 API를 통해, 커밋 이후 후속 처리는 Spring Modulith Event Publication Registry를 통해 수행하며 모듈 간 JPA 연관관계·Repository 접근·DB FK는 금지한다.

**Tech Stack:** Java 25, Gradle 9.6.1, Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring Data JPA, Querydsl, Spring Security, Redis/Redisson, Flyway, H2/Oracle, JUnit 5, Spring Modulith Test, Testcontainers

---

## 실행 원칙과 완료 판정

이 계획은 승인된 설계 문서 `docs/superpowers/specs/2026-09-01-ticket-core-spring-modulith-design.md`의 실행 명세다. 구현 중 선택지가 생기면 설계 문서와 아래 규칙을 우선한다.

- 각 Task는 적색 테스트 → 최소 구현 → 관련 테스트 통과 → 커밋 순서로 끝낸다.
- 사용자가 이미 작업한 변경은 되돌리지 않는다. 시작 시와 각 커밋 전 `git status --short`로 범위를 확인한다.
- 기존에 적용된 Flyway 파일의 내용과 버전은 수정하지 않는다. 이동만 허용하고 새 변경은 새 migration으로 추가한다.
- 같은 부수효과를 custom outbox와 Modulith listener에서 동시에 실행하지 않는다. 흐름별로 테스트를 먼저 만든 뒤 한 번에 cutover한다.
- `shared`에는 `BusinessProblem`, `BusinessException` 이외의 타입을 추가하지 않는다.
- controller, HTTP DTO, JPA entity, repository, Querydsl, Redis, JWT 구현은 모두 해당 모듈의 `internal` 아래에 둔다.
- root package에는 `TicketApplication`과 전역 기술 adapter만 둔다. 전역 기술 adapter는 업무 모델을 import하지 않는다.
- 최종 상태에서 `integrationTest` source set과 Gradle subproject는 없어야 한다.
- 작업 중 임시 adapter나 deprecated bridge가 필요하면 다음 Task로 넘기지 말고 같은 Task 안에서 제거한다.
- 모든 커밋 메시지는 저장소의 한국어 명령형 규칙을 따른다.

최종 의존 DAG는 정확히 다음과 같아야 한다.

```text
metadata  -> catalog, booking, identity
showlike  -> catalog, identity
booking   -> catalog, identity, admission
catalog   -> 없음
identity  -> 없음
admission -> 없음
shared    -> 모든 모듈의 암묵적 shared module
```

## 목표 파일 구조

```text
src/main/java/com/ticket
├── TicketApplication.java
├── configuration/                 # 전역 기술 설정만
├── web/                           # 전역 ProblemDetail handler만
├── booking/
│   ├── package-info.java
│   ├── BookingCatalog.java        # metadata용 공개 계약
│   ├── OrderStarted.java
│   ├── OrderTerminated.java
│   └── internal/{web,application,domain,infrastructure}/...
├── catalog/
│   ├── package-info.java
│   ├── BookingPolicyLookup.java
│   ├── BookingPolicySnapshot.java
│   ├── ShowLookup.java
│   ├── ShowSummary.java
│   ├── CatalogMetadata.java
│   └── internal/{web,application,domain,infrastructure}/...
├── identity/
│   ├── package-info.java
│   ├── AuthenticatedMember.java
│   ├── MemberLookup.java
│   ├── MemberStatus.java
│   ├── IdentityMetadata.java
│   └── internal/{web,application,domain,infrastructure}/...
├── admission/
│   ├── package-info.java
│   ├── AdmissionVerifier.java
│   ├── AdmissionVerification.java
│   └── internal/{application,infrastructure}/...
├── showlike/
│   ├── package-info.java
│   └── internal/{web,application,domain,infrastructure}/...
├── metadata/
│   ├── package-info.java
│   └── internal/{web,application}/...
└── shared/
    ├── package-info.java
    ├── BusinessProblem.java
    └── BusinessException.java
```

공개 계약의 이름은 기존 도메인 용어와 충돌하면 더 구체적으로 바꿀 수 있지만, 역할·방향·불변성은 바꾸지 않는다. 공개 DTO는 모두 불변 `record`로 만들고 JPA/Redis/JWT/Spring Web 타입을 노출하지 않는다.

### Task 1: 기준선 고정과 플랫폼 조합 검증

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle`
- Test: `bootstrap/src/test/java/com/ticket/PlatformCompatibilityTest.java` (Task 2에서 `src/test/java/com/ticket/PlatformCompatibilityTest.java`로 이동)

- [ ] **Step 1: 현재 기준선을 기록한다**

Run:

```powershell
.\gradlew.bat clean test :core:core-infra:integrationTest :bootstrap:integrationTest :bootstrap:bootJar
```

Expected: 기존 브랜치의 테스트와 실행 jar 생성이 통과한다. 실패가 있으면 이번 변경과 무관한 기존 실패인지 먼저 기록하고 원인을 숨기지 않는다.

- [ ] **Step 2: Spring Boot와 Modulith 버전을 고정한다**

`gradle/libs.versions.toml`의 `spring-boot`를 `4.1.1`로 바꾸고 다음 버전과 library alias를 추가한다.

```toml
[versions]
spring-boot = "4.1.1"
spring-modulith = "2.1.1"

[libraries]
spring-modulith-bom = { module = "org.springframework.modulith:spring-modulith-bom", version.ref = "spring-modulith" }
spring-modulith-starter-jpa = { module = "org.springframework.modulith:spring-modulith-starter-jpa" }
spring-modulith-starter-insight = { module = "org.springframework.modulith:spring-modulith-starter-insight" }
spring-modulith-starter-test = { module = "org.springframework.modulith:spring-modulith-starter-test" }
```

- [ ] **Step 3: 아직 멀티프로젝트인 상태에서 조합만 컴파일한다**

실행 애플리케이션이 있는 `bootstrap/build.gradle`에 BOM과 starter를 임시로 연결한다. 최종 의존성은 Task 2에서 root로 옮기므로 이 단계에서는 소스 이동을 하지 않는다.

```groovy
dependencyManagement {
    imports {
        mavenBom libs.spring.modulith.bom.get().toString()
    }
}

dependencies {
    implementation libs.spring.modulith.starter.jpa
    runtimeOnly libs.spring.modulith.starter.insight
    testImplementation libs.spring.modulith.starter.test
}
```

- [ ] **Step 4: platform smoke test를 작성하고 실패를 확인한다**

`PlatformCompatibilityTest`에서 `@SpringBootTest`로 context를 기동한다. 이 테스트는 Boot 4.1.1에서 기존 security/JPA/Redis 설정의 API 파손을 드러내는 용도다.

Run:

```powershell
.\gradlew.bat :bootstrap:test --tests "*PlatformCompatibilityTest"
```

Expected: 최초에는 변경된 Boot API나 구성 충돌이 있으면 FAIL한다.

- [ ] **Step 5: 호환성 파손만 최소 수정한다**

Boot 4.1.1 전환으로 생긴 import/configuration API 변경만 수정한다. 패키지 재배치나 업무 로직 변경은 아직 하지 않는다. `@EntityScan`을 새로 추가하지 않는다. Modulith JPA publication entity 자동 검색을 막기 때문이다.

- [ ] **Step 6: 기준선을 다시 검증한다**

Run:

```powershell
.\gradlew.bat clean test :core:core-infra:integrationTest :bootstrap:integrationTest :bootstrap:bootJar
```

Expected: PASS.

- [ ] **Step 7: 커밋한다**

```powershell
git add gradle/libs.versions.toml build.gradle bootstrap src
git commit -m "build: Spring Boot와 Modulith 기준 버전을 고정한다"
```

### Task 2: 단일 Gradle Spring Boot 프로젝트로 통합

**Files:**
- Modify: `settings.gradle`
- Rewrite: `build.gradle`
- Move: `bootstrap/src/main/**` → `src/main/**`
- Move: `core/**/src/main/**` → `src/main/**`
- Move: `storage/redis-core/src/main/**` → `src/main/**`
- Move: `support/error/src/main/**` → `src/main/**`
- Move: `support/logging/src/main/**` → `src/main/**`
- Move: 모든 `src/test/**`, `src/integrationTest/**` → `src/test/**`
- Move: 통합 테스트 fixture resource → `src/test/resources/**`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/deploy.yml`
- Modify: `Dockerfile`

- [ ] **Step 1: 충돌 없는 이동 manifest를 만든다**

Run:

```powershell
rg --files bootstrap core storage support | Sort-Object
```

같은 상대경로가 겹치면 내용을 비교하고 단일 root 파일로 병합한다. 특히 모든 `application*.yml`, logging resource, `redis.yml`, static asset 642개, Flyway와 테스트 SQL fixture를 누락하지 않는다.

- [ ] **Step 2: 설정 파일을 단일 프로젝트로 바꾼다**

`settings.gradle`은 아래 두 줄만 유지한다.

```groovy
rootProject.name = 'ticket'
```

`build.gradle`은 root에 `java`, Spring Boot, dependency management plugin을 적용하고 Java 25 toolchain, 전체 기존 runtime dependency, Querydsl annotation processor, Modulith BOM/starters, JUnit platform을 선언한다. 최종 의존성 scope는 다음을 보장한다.

```groovy
plugins {
    id 'java'
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

dependencyManagement {
    imports { mavenBom libs.spring.modulith.bom.get().toString() }
}

dependencies {
    implementation libs.spring.modulith.starter.jpa
    runtimeOnly libs.spring.modulith.starter.insight
    testImplementation libs.spring.modulith.starter.test
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') { useJUnitPlatform() }
```

기존 subproject build 파일에서 사용 중인 모든 dependency를 root로 합치되, 더 이상 참조되지 않는 dependency는 마지막 정리 Task에서 제거한다.

- [ ] **Step 3: main source와 resource를 root로 이동한다**

Git 이력을 보존하도록 `git mv`를 사용한다. 같은 package 경로는 같은 root에 합친다. 이 단계에서는 Java package 이름을 바꾸지 않는다.

- [ ] **Step 4: 모든 테스트를 표준 source set으로 이동한다**

기존 unit/integration test를 `src/test/java`, `src/test/resources`에 합친다. `RecordingLockManager` 같은 test fixture도 대응하는 `com.ticket...` test package로 이동한다. `integrationTest` task와 source set 선언을 삭제한다.

- [ ] **Step 5: CI와 container entry를 root artifact로 바꾼다**

CI 핵심 명령은 다음으로 단순화한다.

```powershell
.\gradlew.bat clean test bootJar
```

Linux CI에서는 `./gradlew clean test bootJar`를 사용한다. CI artifact path, deploy workflow의 download path, Dockerfile의 copy path를 모두 `build/libs/*.jar` 또는 `build/libs`로 맞춘다. 어느 한 곳에도 `bootstrap/build/libs`가 남지 않아야 한다.

- [ ] **Step 6: 빈 subproject 디렉터리와 build 파일을 제거한다**

정확히 `bootstrap`, `core`, `storage`, `support` 아래의 이동 완료된 파일만 제거한다. 삭제 전에 다음으로 잔존 파일을 확인한다.

```powershell
rg --files bootstrap core storage support
```

Expected: 추적해야 할 파일이 남아 있지 않다.

- [ ] **Step 7: 단일 프로젝트를 검증한다**

Run:

```powershell
.\gradlew.bat clean test bootJar
.\gradlew.bat projects
```

Expected: build PASS, Gradle projects 출력에 root project만 존재한다.

- [ ] **Step 8: 커밋한다**

```powershell
git add -A
git commit -m "refactor: 애플리케이션을 단일 Gradle 프로젝트로 통합한다"
```

### Task 3: Modulith shell과 공통 오류 계약 구축

**Files:**
- Modify: `src/main/java/com/ticket/TicketApplication.java`
- Create: `src/main/java/com/ticket/shared/package-info.java`
- Create: `src/main/java/com/ticket/shared/BusinessProblem.java`
- Create: `src/main/java/com/ticket/shared/BusinessException.java`
- Create/Move: `src/main/java/com/ticket/web/GlobalProblemDetailHandler.java`
- Create: `src/test/java/com/ticket/ModularityTests.java`
- Create: `src/test/java/com/ticket/web/GlobalProblemDetailHandlerTest.java`
- Remove after migration: 기존 `support.error` package

- [ ] **Step 1: 구조 테스트를 먼저 작성한다**

```java
class ModularityTests {
    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(TicketApplication.class).verify();
    }
}
```

Run:

```powershell
.\gradlew.bat test --tests "com.ticket.ModularityTests"
```

Expected: 아직 모듈 package가 없어 FAIL하거나 발견 모듈이 목표와 달라 FAIL한다.

- [ ] **Step 2: 애플리케이션 root를 선언한다**

```java
package com.ticket;

import org.springframework.modulith.Modulith;

@Modulith(sharedModules = "shared")
public class TicketApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
```

`@SpringBootApplication`을 중복으로 붙이지 않는다. `@Modulith`가 composed annotation이다.

- [ ] **Step 3: shared 계약을 최소 구현한다**

```java
public interface BusinessProblem {
    String module();
    String code();
    String title();
    int status();
    String detail();
}
```

`BusinessProblem`은 Spring Web 타입에 의존하지 않는다. `BusinessException`은 non-null `BusinessProblem` 하나를 보관하고 메시지는 `problem.detail()`로 설정한다.

- [ ] **Step 4: ProblemDetail wire contract 테스트를 작성한다**

MockMvc로 다음을 정확히 검증한다.

```json
{
  "type": "urn:ticket:problem:booking:seat-not-available",
  "title": "좌석을 선택할 수 없습니다",
  "status": 409,
  "detail": "이미 선택되었거나 판매된 좌석입니다",
  "code": "BOOKING_SEAT_NOT_AVAILABLE"
}
```

- [ ] **Step 5: 전역 handler를 구현하고 기존 E-code 기반 handler를 대체한다**

`GlobalProblemDetailHandler`는 구체 module error enum을 import하지 않는다. `BusinessException.problem()`만 읽어 `ProblemDetail`을 만들고, 예상하지 못한 예외는 stack trace와 내부 메시지를 응답에 노출하지 않는다.

- [ ] **Step 6: 각 목표 module의 닫힌 skeleton을 선언한다**

각 module root `package-info.java`에 `@ApplicationModule`을 선언한다. 정확한 allowed dependency는 다음과 같다.

```java
@ApplicationModule(displayName = "Booking", allowedDependencies = {"catalog", "identity", "admission"})
package com.ticket.booking;

import org.springframework.modulith.ApplicationModule;
```

같은 형식으로 catalog/identity/admission은 빈 배열, showlike는 `{"catalog", "identity"}`, metadata는 `{"catalog", "booking", "identity"}`를 선언한다. `shared`는 `@Modulith(sharedModules = "shared")`로 공유되므로 업무 dependency 배열에 넣지 않는다. 어떤 module도 `Type.OPEN`으로 선언하지 않는다.

- [ ] **Step 7: 검증하고 커밋한다**

```powershell
.\gradlew.bat test --tests "com.ticket.ModularityTests" --tests "*GlobalProblemDetailHandlerTest"
git add src/main src/test
git commit -m "refactor: Modulith 경계와 공통 오류 계약을 세운다"
```

Expected: 구조 테스트 PASS. 아직 legacy package가 root 직접 하위에 있다면 그 package는 임시 명시 module로 만들지 말고 이후 이동 Task가 끝날 때까지 구조 테스트의 module set assertion만 보류한다. `verify()` 자체는 항상 활성화한다.

### Task 4: admission 독립 모듈 추출

**Files:**
- Move: 기존 `app/admission/**`, `infra/admission/**`, `AdmissionHeaders` → `src/main/java/com/ticket/admission/internal/**`
- Create: `src/main/java/com/ticket/admission/AdmissionVerifier.java`
- Create: `src/main/java/com/ticket/admission/AdmissionVerification.java`
- Create: `src/test/java/com/ticket/admission/AdmissionModuleTests.java`
- Move/Modify: admission unit tests → `src/test/java/com/ticket/admission/internal/**`

- [ ] **Step 1: 공개 계약 test를 작성한다**

`AdmissionVerifier.verify(long performanceId, long memberId, String token)`이 유효한 token에는 success, 만료·claim mismatch·서명 오류에는 admission 소유 `BusinessException`을 내는 동작을 고정한다. 공개 result는 기술 예외나 JWT claim 객체를 포함하지 않는 불변 record다.

- [ ] **Step 2: STANDALONE module test를 작성한다**

```java
@ApplicationModuleTest
class AdmissionModuleTests {
    @Test
    void bootstraps() {}
}
```

- [ ] **Step 3: JWT와 header 구현을 internal로 이동한다**

기존 검증 동작을 변경하지 않고 public interface 뒤에 구현한다. booking이 header 이름을 알아야 한다면 HTTP adapter가 문자열을 꺼내 `AdmissionVerifier`에 넘기게 하며 admission internal constant를 import하지 않는다.

- [ ] **Step 4: direct dependency가 없는지 검증한다**

```powershell
rg "com\.ticket\.(booking|catalog|identity|showlike|metadata)" src/main/java/com/ticket/admission
.\gradlew.bat test --tests "com.ticket.admission.*"
```

Expected: rg 결과 없음, tests PASS.

- [ ] **Step 5: 커밋한다**

```powershell
git add src/main/java/com/ticket/admission src/test/java/com/ticket/admission
git commit -m "refactor: 입장 검증을 독립 Modulith 모듈로 옮긴다"
```

### Task 5: catalog 소유권과 공개 snapshot API 확립

**Files:**
- Move: performance/show/seat/queue 관련 app/domain/infra/api → `src/main/java/com/ticket/catalog/internal/**`
- Create: `src/main/java/com/ticket/catalog/BookingPolicyLookup.java`
- Create: `src/main/java/com/ticket/catalog/BookingPolicySnapshot.java`
- Create: `src/main/java/com/ticket/catalog/ShowLookup.java`
- Create: `src/main/java/com/ticket/catalog/ShowSummary.java`
- Create: `src/main/java/com/ticket/catalog/CatalogMetadata.java`
- Create: `src/test/java/com/ticket/catalog/CatalogModuleTests.java`
- Create/Move: catalog persistence/unit/web tests

- [ ] **Step 1: 공개 계약의 consumer contract tests를 먼저 작성한다**

`BookingPolicySnapshot`에는 booking의 즉시 판단에 필요한 scalar 값만 둔다: `performanceId`, 예매 가능 여부/시간, queue mode/level, hold 제한, 가격 계산에 필요한 값. `ShowSummary`에는 showlike 응답 조합에 필요한 show/venue 표시 값만 둔다. 컬렉션은 defensive copy한다.

- [ ] **Step 2: catalog entity를 이동하고 module-local audited base를 만든다**

공통 `BaseEntity` 상속을 제거하고 `CatalogAuditedEntity`를 `catalog.internal`에 만든다. Show, Performance, Seat, PerformanceQueuePolicy만 catalog가 소유한다. PerformanceSeat는 이동하지 않는다.

- [ ] **Step 3: 공개 API adapter를 구현한다**

API별로 작은 interface를 유지한다.

```java
public interface BookingPolicyLookup {
    BookingPolicySnapshot getBookingPolicy(long performanceId, List<Long> seatIds);
}

public interface ShowLookup {
    void requireExisting(long showId);
    Map<Long, ShowSummary> getSummaries(Set<Long> showIds);
}
```

빈 batch는 빈 map을 반환하고, 존재하지 않는 ID는 caller가 의미를 결정할 수 있는 공개 result 또는 catalog 소유 exception으로 일관되게 처리한다. JPA entity를 반환하지 않는다.

- [ ] **Step 4: controller와 HTTP DTO를 internal.web로 옮긴다**

Performance/Show/Genre endpoint의 URL, JSON field와 pagination contract는 유지한다.

- [ ] **Step 5: persistence slice와 STANDALONE test를 통과시킨다**

JPA adapter test는 `@DataJpaTest`와 `@ModuleSlicing`을 함께 사용한다. module test에서는 다른 업무 모듈 bean이 없어도 catalog가 기동되어야 한다.

- [ ] **Step 6: 의존성 방향을 검사하고 커밋한다**

```powershell
rg "com\.ticket\.(booking|identity|admission|showlike|metadata)" src/main/java/com/ticket/catalog
.\gradlew.bat test --tests "com.ticket.catalog.*" --tests "com.ticket.ModularityTests"
git add src/main src/test
git commit -m "refactor: 공연 카탈로그 경계와 공개 조회 계약을 확립한다"
```

Expected: catalog에서 다른 업무 모듈 import 0, tests PASS.

### Task 6: identity 소유권과 인증 공개 계약 확립

**Files:**
- Move: auth/member 관련 app/domain/infra/api → `src/main/java/com/ticket/identity/internal/**`
- Create: `src/main/java/com/ticket/identity/AuthenticatedMember.java`
- Create: `src/main/java/com/ticket/identity/MemberLookup.java`
- Create: `src/main/java/com/ticket/identity/MemberStatus.java`
- Create: `src/main/java/com/ticket/identity/IdentityMetadata.java`
- Create: `src/test/java/com/ticket/identity/IdentityModuleTests.java`
- Create/Move: identity persistence/security/web tests

- [ ] **Step 1: 인증 principal과 회원 조회 계약을 test-first로 정의한다**

`AuthenticatedMember`는 `memberId`와 인가에 꼭 필요한 role만 가진 불변 record다. `MemberLookup`은 member entity 대신 공개 `MemberStatus`를 반환하거나 active member를 검증한다. booking/showlike는 identity internal exception을 import하지 않는다.

- [ ] **Step 2: 인증·회원 코드를 identity internal로 이동한다**

Member, MemberSocialAccount, refresh-token 상태, OAuth adapter, password, token/security filter를 identity가 소유한다. 공통 `BaseEntity`는 `IdentityAuditedEntity`로 교체한다.

- [ ] **Step 3: security 설정의 경계를 정리한다**

사용자 인증 해석은 identity 내부에 두되 전역 filter chain이 필요하면 identity가 `SecurityFilterChain` bean을 제공한다. 다른 module controller는 `AuthenticatedMember`만 parameter로 사용하고 JWT/JPA Member를 보지 않는다. WebSocket 설정은 booking에 남긴다.

- [ ] **Step 4: metadata 계약을 구현한다**

Role/SocialProvider 목록을 `IdentityMetadata`가 공개 scalar code/label record로 제공한다. metadata module이 internal enum을 import하지 않게 한다.

- [ ] **Step 5: 독립 검증 후 커밋한다**

```powershell
rg "com\.ticket\.(booking|catalog|admission|showlike|metadata)" src/main/java/com/ticket/identity
.\gradlew.bat test --tests "com.ticket.identity.*" --tests "com.ticket.ModularityTests"
git add src/main src/test
git commit -m "refactor: 회원과 인증을 identity 모듈로 캡슐화한다"
```

### Task 7: booking aggregate와 교차 모듈 호출 재구성

**Files:**
- Move: order/hold/selection/performanceseat/lock 관련 code → `src/main/java/com/ticket/booking/internal/**`
- Create: `src/main/java/com/ticket/booking/BookingCatalog.java`
- Create: `src/test/java/com/ticket/booking/BookingModuleTests.java`
- Modify: PerformanceSeat entity/repository/query
- Modify: order creation orchestration
- Move/Modify: Selection·Hold·Order unit/integration tests

- [ ] **Step 1: 핵심 불변식 회귀 테스트를 먼저 한곳에 모은다**

다음을 각각 이름이 드러나는 테스트로 고정한다.

- 같은 seat를 동시에 선택할 때 한 요청만 성공한다.
- hold 만료/해제 뒤 재선택 가능하다.
- pending order 중복 생성이 차단된다.
- 주문 생성 DB 실패 시 미리 만든 Redis hold가 보상 해제된다.
- 주문 가격은 주문 시작 시점 snapshot으로 보존된다.
- stale release가 새 selection/hold를 제거하지 않는다.

- [ ] **Step 2: booking entity와 audited base를 이동한다**

PerformanceSeat, Selection, Hold, Order, OrderSeat를 booking이 소유한다. `BookingAuditedEntity`를 만들고 module 외부 `BaseEntity` 상속을 제거한다.

- [ ] **Step 3: 교차 module JPA 관계를 scalar ID로 바꾼다**

```text
PerformanceSeat.performance -> long performanceId
PerformanceSeat.seat        -> long seatId
```

JPA annotation에서 `@ManyToOne`과 join column을 제거하고 동일 컬럼 값이 유지되도록 scalar column mapping으로 바꾼다. booking query에서 Performance/Seat table join을 제거한다.

- [ ] **Step 4: CreateOrderValidator의 직접 repository 의존을 공개 API로 바꾼다**

최종 orchestration 순서는 아래와 같아야 한다.

```text
HTTP adapter
  1. Identity MemberLookup: active member 확인 (DB transaction 밖)
  2. Catalog BookingPolicyLookup: 예매 정책/seat 소속/가격 snapshot (밖)
  3. AdmissionVerifier: 정책상 필요할 때 token 검증 (밖)
  4. Booking local read: pending order/PerformanceSeat 확인
  5. Redis selection lock + hold 생성 (booking DB transaction 밖)
  6. Booking 전용 DB transaction: Order/OrderSeat/HoldHistory 저장 + event publish
  7. commit 이후 listener
```

다른 module API 호출과 Redis I/O를 booking write transaction 안에 두지 않는다. transaction method는 booking internal service의 별도 public method가 아니라 package-private component로 분리해 self-invocation 문제를 피한다.

- [ ] **Step 5: booking 조회를 catalog snapshot 조합으로 바꾼다**

selection/status/seat-map/order 응답이 physical seat/show/venue 정보가 필요하면 booking repository는 local ID만 조회하고 catalog 공개 batch API로 조합한다. catalog repository를 직접 import하거나 cross-module Querydsl join을 만들지 않는다.

- [ ] **Step 6: metadata 공개 계약을 구현한다**

`BookingCatalog`는 PerformanceSeatState/HoldState/OrderState의 code와 label snapshot을 반환한다. internal enum 자체를 반환하지 않는다.

- [ ] **Step 7: module test에서 외부 API를 mock한다**

`@ApplicationModuleTest` 기본 STANDALONE, 외부 `BookingPolicyLookup`, `MemberLookup`, `AdmissionVerifier`는 `@MockitoBean`으로 대체한다. 실제 의존 module 조합이 필요한 단 하나의 contract test만 `DIRECT_DEPENDENCIES`를 쓴다.

- [ ] **Step 8: 검증하고 커밋한다**

```powershell
rg "(MemberRepository|PerformanceRepository|ShowRepository)" src/main/java/com/ticket/booking
rg "@(ManyToOne|OneToOne).*Performance|@(ManyToOne|OneToOne).*Seat" src/main/java/com/ticket/booking
.\gradlew.bat test --tests "com.ticket.booking.*" --tests "com.ticket.ModularityTests"
git add src/main src/test
git commit -m "refactor: 예매 생명주기를 booking 모듈에 응집한다"
```

Expected: 두 rg에서 교차 모듈 사용 0, tests PASS.

### Task 8: Modulith 이벤트와 JPA Publication Registry로 전환

**Files:**
- Create: `src/main/java/com/ticket/booking/OrderStarted.java`
- Create: `src/main/java/com/ticket/booking/OrderTerminated.java`
- Create: `src/main/java/com/ticket/booking/internal/application/BookingEventListeners.java`
- Create: `src/main/java/com/ticket/configuration/EventPublicationMaintenance.java`
- Modify: order create/terminate transaction services
- Remove: custom outbox publisher/repository/entity/job code
- Create: publication tests under `src/test/java/com/ticket/booking/**`

- [ ] **Step 1: 이벤트 schema를 먼저 고정한다**

두 이벤트는 stable scalar payload만 갖는다.

```java
public record OrderStarted(
        UUID eventId,
        int schemaVersion,
        long orderId,
        long memberId,
        String holdKey,
        Set<Long> performanceSeatIds,
        Instant occurredAt) {
    public OrderStarted { performanceSeatIds = Set.copyOf(performanceSeatIds); }
}

public record OrderTerminated(
        UUID eventId,
        int schemaVersion,
        long orderId,
        long memberId,
        String holdKey,
        Set<Long> performanceSeatIds,
        String reason,
        Instant occurredAt) {
    public OrderTerminated { performanceSeatIds = Set.copyOf(performanceSeatIds); }
}
```

초기 `schemaVersion`은 1이다. entity, lazy proxy, repository, exception을 payload에 넣지 않는다.

- [ ] **Step 2: transaction rollback/publication 원자성 테스트를 작성한다**

`PublishedEvents`와 실제 publication repository 상태를 사용해 다음을 검증한다.

- booking DB transaction 성공 시 event/publication이 함께 저장된다.
- transaction rollback 시 order와 publication이 모두 없다.
- `PendingOrderCreationResult`에는 더 이상 outbox ID가 없다.

- [ ] **Step 3: custom outbox 대신 transaction 내부에서 event를 발행한다**

`ApplicationEventPublisher.publishEvent(...)`는 Order/OrderSeat/HoldHistory 저장과 같은 booking transaction 안에서 호출한다. manual post-commit notifier를 제거한다.

- [ ] **Step 4: 후속 처리를 `@ApplicationModuleListener`로 이동한다**

selection 정리, Redis hold release, WebSocket 발행을 명시적인 listener method로 분리한다. listener는 실패를 catch-and-log로 삼키지 않고 throw하여 registry가 FAILED로 기록하게 한다.

- [ ] **Step 5: listener 멱등성과 stale-event 방어 테스트를 작성한다**

동일 `eventId`가 여러 번 전달되어도 최종 상태와 WebSocket 의미가 한 번 처리한 것과 같아야 한다. Redis release 직전에 현재 holdKey/order 상태를 다시 확인하고, 예전 event가 새 hold/selection을 지우지 못하게 한다. 필요한 local processed marker는 booking이 소유하며 event ID unique constraint를 둔다.

- [ ] **Step 6: registry 운영 component를 구현한다**

```java
@Component
class EventPublicationMaintenance {
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    void purgeArchive() {
        completedEventPublications.deletePublicationsOlderThan(Duration.ofDays(30));
    }

    @Scheduled(fixedDelayString = "PT1M")
    void resubmitFailed() {
        failedEventPublications.resubmit(
            ResubmissionOptions.defaults()
                .withBatchSize(100)
                .withMaxInFlight(4)
                .withFilter(it -> it.getCompletionAttempts() <= 10));
    }
}
```

실제 2.1.1 API signature에 맞춰 컴파일하되 정책값은 바꾸지 않는다. 10회를 초과한 publication은 자동 대상에서 제외하고 metric/structured log로 alert 가능한 신호를 남긴다. 수동 복구 runbook에서 ID를 확인할 수 있어야 한다.

- [ ] **Step 7: 설정을 고정한다**

모든 profile 공통 설정:

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

broker dependency와 `@Externalized`는 추가하지 않는다.

- [ ] **Step 8: 성공·실패·재처리 Scenario test를 통과시킨다**

`Scenario`로 listener 완료를 기다리고, 첫 시도 실패 후 publication FAILED, 재제출 성공 후 COMPLETED/ARCHIVED, 11회째 자동 제외를 검증한다. 고정 clock과 deterministic fake를 사용하며 `Thread.sleep`을 쓰지 않는다.

- [ ] **Step 9: custom outbox를 흐름별로 제거하고 커밋한다**

```powershell
rg -i "outbox|postcommit|pendingordercreationresult" src/main src/test
.\gradlew.bat test --tests "com.ticket.booking.*" --tests "*EventPublication*"
git add src/main src/test
git commit -m "refactor: 예매 후속 처리를 Modulith 이벤트로 전환한다"
```

Expected: custom outbox runtime code 0. migration 명칭의 historical outbox 문자열은 이 Task에서는 허용한다.

### Task 9: showlike를 scalar-ID 모듈로 추출

**Files:**
- Move: showlike app/domain/infra/api → `src/main/java/com/ticket/showlike/internal/**`
- Modify: ShowLike entity and Querydsl repository
- Create: `src/test/java/com/ticket/showlike/ShowLikeModuleTests.java`
- Create/Move: showlike persistence/application/web tests

- [ ] **Step 1: 중복과 batch enrichment test를 먼저 작성한다**

`(memberId, showId)` 중복 거부, active member 확인, show 존재 확인, liked-list 순서/커서 유지, catalog batch 호출 1회를 검증한다.

- [ ] **Step 2: JPA 관계를 scalar ID로 바꾼다**

```text
ShowLike.member -> long memberId
ShowLike.show   -> long showId
```

`ShowLikeAuditedEntity`를 만들고 Member/Show entity import와 cross-module DB FK를 제거한다. unique constraint는 `(member_id, show_id)`로 유지한다.

- [ ] **Step 3: application flow를 공개 API로 바꾼다**

write 전 `MemberLookup`, `ShowLookup.requireExisting`를 호출한다. liked-list는 local ShowLike refs를 먼저 page한 다음 show IDs를 한 번의 `ShowLookup.getSummaries`로 조회하여 원래 순서대로 조합한다. Querydsl에서 Show/Venue join을 제거한다.

- [ ] **Step 4: STANDALONE/slice test를 통과시키고 커밋한다**

```powershell
rg "(MemberRepository|ShowRepository|QShow|QMember)" src/main/java/com/ticket/showlike
.\gradlew.bat test --tests "com.ticket.showlike.*" --tests "com.ticket.ModularityTests"
git add src/main src/test
git commit -m "refactor: 좋아요를 독립 showlike 모듈로 분리한다"
```

### Task 10: metadata를 공개 계약 조합 모듈로 추출

**Files:**
- Move: MetaController/GetMetaCodesUseCase → `src/main/java/com/ticket/metadata/internal/**`
- Create: `src/test/java/com/ticket/metadata/MetadataModuleTests.java`
- Remove: 사용되지 않는 CommonCode JPA entity/repository code

- [ ] **Step 1: metadata HTTP contract test를 작성한다**

기존 endpoint의 응답 키와 code/label을 보존하면서 catalog, booking, identity 공개 metadata API를 각각 한 번 호출하는지 검증한다.

- [ ] **Step 2: internal enum import를 제거한다**

metadata는 `CatalogMetadata`, `BookingCatalog`, `IdentityMetadata`만 주입받아 결과를 조합한다. 다른 module의 internal enum/entity/repository를 import하지 않는다.

- [ ] **Step 3: 사용되지 않는 CommonCode persistence code를 제거한다**

코드 사용처가 0임을 `rg`로 확인한 뒤 Java code만 제거한다. 운영 DB에 존재할 수 있는 legacy table을 추측으로 drop하지 않는다.

- [ ] **Step 4: STANDALONE module test를 통과시키고 커밋한다**

```powershell
rg "com\.ticket\.(catalog|booking|identity)\.internal" src/main/java/com/ticket/metadata
.\gradlew.bat test --tests "com.ticket.metadata.*" --tests "com.ticket.ModularityTests"
git add src/main src/test
git commit -m "refactor: 메타데이터 조회를 공개 계약 조합으로 바꾼다"
```

### Task 11: module-aware Flyway와 물리 데이터 경계 완성

**Files:**
- Move: 기존 공통 migration → `src/main/resources/db/migration/__root/**`
- Move: 기존 H2 migration → `src/main/resources/db/migration-vendor/h2/__root/**`
- Move: 기존 Oracle migration → `src/main/resources/db/migration-vendor/oracle/__root/**`
- Create: `src/main/resources/db/migration/booking/**`
- Create: `src/main/resources/db/migration/catalog/**`
- Create: `src/main/resources/db/migration/identity/**`
- Create: `src/main/resources/db/migration/showlike/**`
- Modify: `src/main/resources/application*.yml`
- Create: migration integration tests

- [ ] **Step 1: 적용 이력이 있는 migration을 내용 변경 없이 이동한다**

기존 V2 및 H2/Oracle V3~V7 파일의 checksum이 바뀌지 않도록 byte-for-byte로 각 configured location의 `__root`에 이동한다. 즉 V2는 `db/migration/__root`, vendor migration은 `db/migration-vendor/{h2|oracle}/__root`에 둔다. 이동 전 원본 hash manifest를 만들고 이동 후 대상 hash와 비교한다.

```powershell
$migrationSourceRoot = (Resolve-Path bootstrap/src/main/resources).Path
$migrationSources = Get-ChildItem bootstrap/src/main/resources/db -Recurse -File | Where-Object Name -Match '^V[2-7]__'
$migrationSources | ForEach-Object { $key = $_.FullName.Substring($migrationSourceRoot.Length + 1).Replace('\', '/').Replace('/migration-vendor/h2/', '/migration-vendor/h2/__root/').Replace('/migration-vendor/oracle/', '/migration-vendor/oracle/__root/').Replace('/migration/', '/migration/__root/'); "$key $((Get-FileHash -Algorithm SHA256 $_.FullName).Hash)" } | Sort-Object | Set-Content -Encoding utf8 build/migration-before.sha256
# git mv와 디렉터리 재배치를 수행한 뒤
$migrationTargetRoot = (Resolve-Path src/main/resources).Path
$migrationTargets = Get-ChildItem src/main/resources/db -Recurse -File | Where-Object Name -Match '^V[2-7]__'
$migrationTargets | ForEach-Object { $key = $_.FullName.Substring($migrationTargetRoot.Length + 1).Replace('\', '/'); "$key $((Get-FileHash -Algorithm SHA256 $_.FullName).Hash)" } | Sort-Object | Set-Content -Encoding utf8 build/migration-after.sha256
Compare-Object (Get-Content build/migration-before.sha256) (Get-Content build/migration-after.sha256)
```

Expected: `Compare-Object` 출력 없음.

- [ ] **Step 2: registry DDL을 `__root`의 새 버전으로 추가한다**

JPA starter 2.1.1의 `JpaEventPublication`, `DefaultJpaEventPublication`, `ArchivedJpaEventPublication` mapping을 기준으로 `EVENT_PUBLICATION`, `EVENT_PUBLICATION_ARCHIVE`를 만든다. 필드는 `id`, `publication_date`, `listener_id`, `serialized_event`, `event_type`, `completion_date`, `last_resubmission_date`, `completion_attempts`, `status`다.

타입을 추측하지 않는다. H2와 Oracle 각각에 대해 임시 test profile에서 Hibernate schema export를 실행해 2.1.1/Boot 4.1.1이 생성하는 column type/nullability를 캡처한 후 Flyway DDL로 옮긴다. 특히 UUID는 H2의 native UUID/BINARY(16)와 Oracle RAW(16) mapping, `serialized_event`는 CLOB, `status`는 문자열 enum mapping을 검증한다. 그 다음 `ddl-auto=validate`로 두 table 모두 validation을 통과시킨다. Hibernate create는 DDL 확인용 test에서만 쓰고 커밋되는 운영 설정에는 두지 않는다.

- [ ] **Step 3: module별 신규 migration을 소유 module 폴더에 둔다**

교차 JPA 관계를 scalar column으로 바꾸는 schema change, module 간 FK 제거, 필요한 unique/index를 각각 `booking`/`showlike`에 새 migration으로 추가한다. 공통 SQL은 `db/migration/{module}`, DB별 SQL은 `db/migration-vendor/{h2|oracle}/{module}`에 둔다. 새 migration 버전은 각 configured location과 module history의 조합에서 충돌하지 않게 1부터 시작하고 같은 module 안에서만 증가한다.

- [ ] **Step 4: custom outbox drop migration을 마지막에 추가한다**

Task 8의 publication success/failure/retry 테스트가 통과한 뒤에만 custom outbox table/index를 새 `booking` migration으로 제거한다. 기존 V5~V7은 수정하지 않는다. 불명확한 legacy table은 삭제하지 않는다.

- [ ] **Step 5: Modulith runtime Flyway 설정을 켠다**

```yaml
spring:
  modulith:
    runtime:
      flyway-enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
```

production은 반드시 validate다. test도 migration 검증 test에서는 validate를 사용한다.

- [ ] **Step 6: module slicing migration test를 작성한다**

각 persistence module의 `@DataJpaTest @ModuleSlicing`에서 root + 해당 module migration만으로 context/schema가 생성되고 CRUD가 동작하는지 검증한다. catalog test가 booking/showlike migration을 필요로 하거나 그 반대면 실패로 간주한다.

- [ ] **Step 7: H2와 Oracle 호환성을 검증한다**

H2 전체 test를 통과시키고, 저장소의 기존 Oracle Testcontainers/profile이 있으면 동일 migration을 실행한다. Oracle 환경이 CI에 없다면 Testcontainers 기반 migration-only test를 추가해 CI에서 실행한다.

- [ ] **Step 8: 커밋한다**

```powershell
.\gradlew.bat clean test
git add src/main/resources src/test
git commit -m "refactor: Flyway 이력을 Modulith 모듈 소유권에 맞춘다"
```

### Task 12: 모듈 테스트·문서화·runtime insight 완성

**Files:**
- Create/Finalize: `src/test/java/com/ticket/*ModuleTests.java`
- Create: `src/test/java/com/ticket/DocumentationTests.java`
- Create: `src/test/java/com/ticket/ApplicationContextLoadTest.java`
- Modify: actuator/security/application profile configuration
- Remove/Reduce: legacy architecture-wide ArchUnit tests

- [ ] **Step 1: 정확한 module set과 DAG assertion을 추가한다**

`ModularityTests`는 `verify()`뿐 아니라 발견된 module 이름이 정확히 7개이고, 업무 dependency가 승인된 DAG와 일치하는지 assertion한다. open module이 하나라도 있으면 실패한다.

- [ ] **Step 2: module별 STANDALONE test를 완성한다**

booking, catalog, identity, admission, showlike, metadata에 최소 하나씩 둔다. shared는 독립 업무 bootstrap 대상이 아니므로 구조 테스트로 검증한다. 외부 API는 `@MockitoBean`, 의도적인 통합만 DIRECT_DEPENDENCIES, ALL_DEPENDENCIES는 전체 조합 이유가 있는 소수 테스트에만 사용한다.

- [ ] **Step 3: Documenter test를 작성한다**

`ApplicationModules.of(TicketApplication.class)`와 `Documenter`로 전체 dependency diagram, 각 module canvas, exposed beans, events를 `build/spring-modulith-docs` 아래에 생성한다. 생성물은 CI artifact이며 source 문서로 commit하지 않는다.

- [ ] **Step 4: insight와 actuator 접근을 검증한다**

`spring-modulith-starter-insight`를 runtime scope에 유지한다. `/actuator/modulith`는 public permit-all 목록에 넣지 않고 관리망/인증 actuator chain으로만 접근 가능하게 한다. module API trace와 event publication metric이 등록되는 smoke test를 둔다.

- [ ] **Step 5: profile별 runtime verification을 고정한다**

local/test/staging:

```yaml
spring.modulith.runtime.verification-enabled: true
```

production:

```yaml
spring.modulith.runtime.verification-enabled: false
```

CI의 `ModularityTests`가 권위 있는 gate이며 production artifact는 해당 CI를 통과한 동일 jar만 배포한다.

- [ ] **Step 6: legacy ArchUnit 중복을 정리한다**

기존 계층형 module 구조를 복제하는 ArchUnit rule은 제거한다. Spring Modulith가 검사하지 않는 domain purity rule만 구체적 이유와 함께 남긴다.

- [ ] **Step 7: 검증하고 커밋한다**

```powershell
.\gradlew.bat test --tests "com.ticket.ModularityTests" --tests "com.ticket.DocumentationTests" --tests "*ModuleTests" --tests "*ApplicationContextLoadTest"
git add src/main src/test build.gradle
git commit -m "test: Modulith 경계와 운영 가시성을 검증한다"
```

### Task 13: legacy package와 의존성 제거

**Files:**
- Remove: legacy `com.ticket.core`, `com.ticket.bootstrap`, `com.ticket.storage`, `com.ticket.support` package remnants
- Modify: `build.gradle`, `gradle/libs.versions.toml`
- Modify: imports across `src/main`, `src/test`

- [ ] **Step 1: 잔존 legacy symbol을 전수 조사한다**

```powershell
rg "package com\.ticket\.(core|bootstrap|storage|support)" src
rg "import com\.ticket\.(core|bootstrap|storage|support)" src
rg -i "outbox|integrationTest" build.gradle settings.gradle src .github Dockerfile
```

Expected: 발견된 항목을 모두 소유 module/internal package로 옮기거나 historical migration/comment처럼 남아야 하는 이유를 확인한다.

- [ ] **Step 2: cross-module persistence coupling을 정적으로 검사한다**

```powershell
rg "import com\.ticket\.[^.]+\.internal" src/main/java
rg "@(ManyToOne|OneToOne|OneToMany|ManyToMany)" src/main/java/com/ticket
rg "Repository" src/main/java/com/ticket/booking src/main/java/com/ticket/showlike src/main/java/com/ticket/metadata
```

첫 명령 결과는 0이어야 한다. JPA 관계 결과는 같은 module 내부 관계만 허용한다. repository 결과는 자기 module repository만 허용한다.

- [ ] **Step 3: 더 이상 쓰지 않는 dependency와 설정을 제거한다**

custom outbox, 옛 multi-project, 중복 ArchUnit, 불필요한 library를 제거한다. Querydsl, Redis, security 등 실제 사용 dependency는 단일 root build에 유지한다.

- [ ] **Step 4: shared 오염을 검사한다**

```powershell
rg --files src/main/java/com/ticket/shared
```

Expected: `package-info.java`, `BusinessProblem.java`, `BusinessException.java`만 존재한다.

- [ ] **Step 5: full test 후 커밋한다**

```powershell
.\gradlew.bat clean test bootJar
git add -A
git commit -m "refactor: 기존 계층 모듈과 임시 호환 코드를 제거한다"
```

### Task 14: 운영·개발 문서와 저장소 지침 갱신

**Files:**
- Create: `docs/adr/0003-spring-modulith-application-module-boundaries.md`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `CONTEXT.md` if architecture facts are present
- Modify: `docs/architecture.md`
- Modify: `docs/development.md`
- Modify: `docs/validation.md`
- Modify: `docs/core-booking-lifecycle.md`
- Modify: `docs/testing.md`
- Modify: `docs/operations.md`
- Modify: `.claude/skills/place-code/SKILL.md`
- Modify: `.claude/skills/verify/SKILL.md`
- Modify: `.claude/skills/commit-pr/**` only if path/commands are stale

- [ ] **Step 1: ADR로 이전 결정을 명시적으로 대체한다**

ADR 0003에는 단일 Gradle project, package-based closed module, DAG, scalar cross-module refs, public API/event, JPA registry, module-aware Flyway를 기록한다. ADR 0001/0002의 어떤 부분을 supersede하는지 명시하되 과거 문서는 삭제하지 않는다.

- [ ] **Step 2: 개발자 문서를 실제 명령과 구조로 바꾼다**

모든 `:bootstrap:*`, `:core:*`, `integrationTest` 명령을 root `test`, `bootJar`로 바꾼다. 새 코드 배치 규칙은 module root public/internal 캡슐화와 allowed dependency를 설명한다.

- [ ] **Step 3: 업무와 운영 runbook을 갱신한다**

Order transaction 순서, Redis 보상, event publication 상태, 1분 재제출, 10회 자동 중단, 30일 archive purge, 수동 재처리와 alert 확인 절차를 기록한다. broker/outbox externalization은 현재 범위가 아님을 명시한다.

- [ ] **Step 4: repository-local skills를 새 구조로 갱신한다**

`place-code`는 기능별 module root/internal 규칙을, `verify`는 `clean test bootJar`, ModularityTests, module/slice/Scenario 검증을 가리키게 한다. junction인 `.agents/skills`가 아니라 Git이 추적하는 `.claude/skills` 파일을 수정한다.

- [ ] **Step 5: 문서의 stale path를 검사한다**

```powershell
rg ":(bootstrap|core|storage|support):|integrationTest|core-(api|app|domain|infra)" AGENTS.md README.md CONTEXT.md docs .claude/skills
```

Expected: archive와 역사 설명을 제외한 실행 지침에서 결과 0.

- [ ] **Step 6: 커밋한다**

```powershell
git add AGENTS.md README.md CONTEXT.md docs .claude/skills
git commit -m "docs: Spring Modulith 개발과 운영 지침을 반영한다"
```

### Task 15: 최종 수용 검증과 감사

**Files:**
- Modify: 발견된 결함의 해당 source/test/doc만

- [ ] **Step 1: clean build를 실행한다**

```powershell
.\gradlew.bat clean test bootJar
```

Expected: PASS, `build/libs`에 실행 jar 생성.

- [ ] **Step 2: Modulith 산출물과 구조를 확인한다**

```powershell
Get-ChildItem build/spring-modulith-docs -Recurse
.\gradlew.bat test --tests "com.ticket.ModularityTests"
```

Expected: dependency diagram/canvas 생성, 구조 검증 PASS.

- [ ] **Step 3: 완료 조건을 정적 감사한다**

```powershell
.\gradlew.bat projects
rg "import com\.ticket\.[^.]+\.internal" src/main/java
rg "package com\.ticket\.(core|bootstrap|storage|support)" src
rg --files src/main/java/com/ticket/shared
rg -i "outbox|integrationTest" build.gradle settings.gradle src/main src/test .github Dockerfile
```

Expected:

- Gradle root project 하나만 존재한다.
- cross-module internal import 0.
- legacy package 0.
- shared 파일 정확히 3개.
- custom outbox runtime code와 integrationTest source set 0. historical migration명은 허용.

- [ ] **Step 4: 데이터 경계를 감사한다**

H2/Oracle migration test, `ddl-auto=validate`, module slicing tests가 모두 통과하는지 확인한다. DB metadata test로 PerformanceSeat→Performance/Seat, ShowLike→Member/Show의 FK가 존재하지 않음을 검증한다.

- [ ] **Step 5: 핵심 업무 회귀를 재실행한다**

Selection 경쟁, Hold 만료/보상, Order 중복/가격 snapshot, event 실패/재시도/멱등, showlike 중복/페이지 순서, security/ProblemDetail contract를 명시적으로 선택 실행한 뒤 full suite도 다시 실행한다.

- [ ] **Step 6: working tree와 commit history를 확인한다**

```powershell
git status --short
git log --oneline --decorate -15
```

Expected: working tree clean. 각 Task가 검토 가능한 작은 커밋으로 남아 있다.

- [ ] **Step 7: 최종 보고를 작성한다**

보고에는 변경 요약, 최종 module DAG, 실행한 검증 명령과 결과, migration/운영 주의사항, 남은 범위가 정말 있다면 그 이유만 포함한다. 테스트를 실행하지 못한 항목을 통과한 것처럼 표현하지 않는다.

## 수용 기준 체크리스트

- [ ] Spring Boot 4.1.1, Spring Modulith 2.1.1, Java 25 조합이다.
- [ ] 단일 Gradle Spring Boot project만 존재한다.
- [ ] `@Modulith(sharedModules = "shared")` root와 정확히 7개 module이 존재한다.
- [ ] 승인된 DAG 밖 의존, 순환 의존, open module, cross-module internal import가 없다.
- [ ] module root 공개 API는 불변 scalar snapshot/event만 노출한다.
- [ ] cross-module JPA 관계와 DB FK가 0이다.
- [ ] 다른 module repository/table 직접 접근이 0이다.
- [ ] Order 생성의 외부 API/Redis I/O가 booking DB write transaction 밖에 있다.
- [ ] custom outbox가 JPA Event Publication Registry로 완전히 대체되었다.
- [ ] ARCHIVE, staleness, retry 1m/100/4/10, purge 30d 정책과 멱등 listener가 검증된다.
- [ ] broker와 `@Externalized`가 추가되지 않았다.
- [ ] 기존 migration checksum은 보존되고 신규 migration은 module별로 실행된다.
- [ ] 운영 ORM은 `ddl-auto=validate`다.
- [ ] ProblemDetail type/code 규칙과 module-owned problem 원칙이 지켜진다.
- [ ] 각 업무 module의 STANDALONE test, `@ModuleSlicing`, Scenario/PublishedEvents, Documenter가 통과한다.
- [ ] local/test/staging runtime verification은 true, production은 false다.
- [ ] actuator Modulith endpoint가 외부에 무인증 노출되지 않는다.
- [ ] Selection·Hold·Order의 동시성·보상·만료 불변식이 회귀 테스트로 보존된다.
- [ ] `clean test bootJar`가 통과하고 working tree가 clean하다.
