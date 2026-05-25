# Flyway Baseline Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 Oracle 운영 DB를 baseline으로 보존하면서 `core:core-api`에 Flyway 마이그레이션 실행 기반을 추가한다.

**Architecture:** Flyway는 Spring Boot 실행 모듈인 `core:core-api`에만 연결한다. 기존 운영 스키마는 최초 도입 배포에서만 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`를 명시해 Flyway 이력에 등록하고, 이후 구조 변경은 `db/migration`의 versioned migration으로 누적한다. `local`은 현재 H2 + Hibernate `create` + seed loader 흐름을 유지하고 Flyway를 비활성화해 개발용 DB 초기화 방식을 바꾸지 않는다.

**Tech Stack:** Java 25, Spring Boot 4.0.2, Gradle, Flyway, Oracle, H2 local profile.

---

## Chunk 1: Flyway 설정과 검증

### Task 1: 설정 회귀 테스트 추가

**Files:**
- Create: `core/core-api/src/test/java/com/ticket/core/config/FlywayConfigurationTest.java`

- [ ] **Step 1: Write the failing test**

검증할 내용:
- Flyway core 클래스가 classpath에 있어야 한다.
- Oracle database module 클래스가 classpath에 있어야 한다.
- 공통 설정은 accidental migration을 막기 위해 Flyway 기본값을 비활성화한다.
- local은 기존 Hibernate create 흐름을 유지하고 Flyway를 끈다.
- prod는 기본 `baseline-on-migrate=false`, `baseline-version=1`, `clean-disabled=true`로 운영 DB를 보호하고 최초 도입 시에만 환경 변수로 baseline을 허용한다.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:core-api:test --tests "*FlywayConfigurationTest"`

Expected: FAIL. Flyway 의존성 또는 `spring.flyway` 설정이 아직 없다.

- [ ] **Step 3: Implement minimal configuration**

수정:
- `core/core-api/build.gradle`
- `core/core-api/src/main/resources/application.yml`
- `core/core-api/src/main/resources/application-local.yml`
- `core/core-api/src/main/resources/application-prod.yml`
- `core/core-api/src/main/resources/db/migration/.gitkeep`

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:core-api:test --tests "*FlywayConfigurationTest"`

Expected: PASS.

### Task 2: 운영 문서 갱신

**Files:**
- Modify: `docs/operations.md`

- [ ] **Step 1: Document baseline workflow**

운영 DB가 이미 존재하는 경우 최초 배포 전에 백업, 권한, `flyway_schema_history` 생성, `baseline-on-migrate` 동작, 이후 migration 작성 규칙을 명시한다.

- [ ] **Step 2: Static verification**

Run: `rg -n "Flyway|baseline|flyway_schema_history" docs/operations.md`

Expected: 관련 운영 절차가 검색된다.

### Task 3: Final verification

- [ ] **Step 1: Compile API module**

Run: `.\gradlew.bat :core:core-api:compileJava`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run focused tests**

Run: `.\gradlew.bat :core:core-api:test --tests "*FlywayConfigurationTest"`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Check diff**

Run: `git diff --check`

Expected: no whitespace errors.
