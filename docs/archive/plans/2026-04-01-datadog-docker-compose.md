> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-04-01. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Datadog Docker Compose Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Docker Compose 기반 운영 서버에서 Datadog Agent 중심 구조를 붙일 수 있도록 애플리케이션 코드와 이미지 준비를 완료한다.

**Architecture:** `core-api`는 Actuator Prometheus endpoint를 노출하고 stdout 로그를 유지한다. Docker 이미지는 `dd-java-agent.jar`를 포함하되, 실제 trace 활성화와 Agent 수집 설정은 운영 compose에서 제어한다.

**Tech Stack:** Spring Boot Actuator, Micrometer Prometheus, Spring Security, Docker, Datadog Java Agent, JUnit 5, MockMvc

---

## Chunk 1: 앱 관측 endpoint 준비

### Task 1: actuator 보안 정책 테스트 추가

**Files:**
- Create: `core/core-api/src/test/java/com/ticket/core/config/security/ActuatorSecurityConfigTest.java`

- [ ] `@WebMvcTest` 기반 테스트를 추가한다.
- [ ] `/actuator/health`, `/actuator/info`, `/actuator/prometheus`는 인증 없이 접근 가능하다는 실패 테스트를 먼저 작성한다.
- [ ] 비공개 API는 계속 `401`이라는 테스트를 함께 둔다.
- [ ] 관련 테스트만 실행해 red를 확인한다.

### Task 2: actuator와 prometheus endpoint 노출 구현

**Files:**
- Modify: `core/core-api/build.gradle`
- Modify: `core/core-api/src/main/resources/application.yml`
- Modify: `core/core-api/src/main/java/com/ticket/core/config/security/SecurityConfig.java`

- [ ] `spring-boot-starter-actuator`와 `micrometer-registry-prometheus`를 추가한다.
- [ ] `health`, `info`, `prometheus` endpoint만 노출하도록 설정한다.
- [ ] actuator 경로를 최소 허용 경로에 추가한다.
- [ ] Task 1 테스트를 다시 실행해 green을 확인한다.

## Chunk 2: Docker 이미지에 Java tracer 준비

### Task 3: Datadog Java agent 포함 테스트 가능한 구조로 정리

**Files:**
- Modify: `Dockerfile`

- [ ] `dd-java-agent.jar`를 이미지에 포함하되 기본 실행 흐름은 유지하는 방향으로 수정한다.
- [ ] 운영 compose에서 `JAVA_TOOL_OPTIONS=-javaagent:...`만 주입하면 trace가 켜질 수 있게 만든다.
- [ ] 불필요한 런타임 변경 없이 기존 이미지 실행 방식이 유지되는지 확인한다.

## Chunk 3: 검증

### Task 4: 변경 검증

**Files:**
- Test: `core/core-api/src/test/java/com/ticket/core/config/security/ActuatorSecurityConfigTest.java`

- [ ] `./gradlew :core:core-api:test --tests com.ticket.core.config.security.ActuatorSecurityConfigTest`를 실행한다.
- [ ] `./gradlew :core:core-api:compileJava`를 실행한다.
- [ ] 결과를 확인하고 남은 운영 compose 반영 항목을 정리한다.
