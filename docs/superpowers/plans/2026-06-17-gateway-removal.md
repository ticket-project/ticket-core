# Gateway Removal Implementation Plan

> 결정 기록: Gateway 제거 작업 당시의 구현 계획입니다. 현재 실행 방법과 서비스 계약은 루트 `README.md`, `docs/development.md`, 형제 저장소 `../ticket-queue/README.md`를 기준으로 확인하세요.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the runtime dependency on `ticket-gateway` by letting core and queue validate external access tokens directly and by binding queue/admission tokens to the authenticated member.

**Architecture:** Core validates external access tokens into `MemberPrincipal`; queue validates the same access tokens into `AuthenticatedMember`. Queue-issued queue/admission tokens carry the member id, and core admission validation checks both `performanceId` and `memberId`. The shared `ticket-common`/Passport layer is removed.

**Tech Stack:** Java 25, Spring Boot, Spring Security core/web filters, jjwt, Gradle, Next.js/TypeScript.

**Note:** The user explicitly asked not to commit yet. Do not run `git commit` during this implementation.

---

### Task 1: Core Direct Access Token Authentication

**Files:**
- Create: `ticket/core/core-api/src/main/java/com/ticket/core/config/security/AccessTokenAuthenticationFilter.java`
- Modify: `ticket/core/core-api/src/main/java/com/ticket/core/config/security/SecurityConfig.java`
- Test: `ticket/core/core-api/src/test/java/com/ticket/core/config/security/JwtTokenServiceTest.java`

- [ ] Write a failing test proving a `Bearer` access token becomes a `MemberPrincipal`.
- [ ] Run the focused test and verify it fails before the local filter/parser change.
- [ ] Implement `AccessTokenAuthenticationFilter` using `JwtTokenService.parse`.
- [ ] Change `SecurityConfig` to install the local access-token filter on `Authorization`.
- [ ] Run the focused security tests.

### Task 2: Admission Token Member Binding in Core

**Files:**
- Modify: `ticket/core/core-api/src/main/java/com/ticket/core/config/admission/AdmissionTokenValidator.java`
- Modify: `ticket/core/core-api/src/main/java/com/ticket/core/api/controller/PerformanceController.java`
- Modify: `ticket/core/core-api/src/main/java/com/ticket/core/api/controller/SeatSelectionController.java`
- Modify: `ticket/core/core-api/src/main/java/com/ticket/core/api/controller/OrderController.java`
- Modify: `ticket/core/core-api/src/main/java/com/ticket/core/api/controller/HoldController.java`
- Test: `ticket/core/core-api/src/test/java/com/ticket/core/config/admission/AdmissionTokenValidatorTest.java`

- [ ] Write failing tests that queue-required performances call `verifyFor(admissionToken, memberId, performanceId)`.
- [ ] Run the focused admission validator test and verify it fails.
- [ ] Change `AdmissionTokenValidator.validate` to accept `memberId`.
- [ ] Update controllers to pass `memberPrincipal.memberId()`.
- [ ] Run focused controller/admission tests.

### Task 3: Queue Direct Access Token Authentication

**Files:**
- Modify: `ticket-queue/src/main/java/com/ticket/queue/QueueApiApplication.java`
- Create: `ticket-queue/src/main/java/com/ticket/queue/config/QueueJwtProperties.java`
- Create: `ticket-queue/src/main/java/com/ticket/queue/config/QueueAccessTokenAuthConfig.java`
- Test: `ticket-queue/src/test/java/com/ticket/queue/api/AdmissionControllerTest.java`

- [ ] Change the controller test to authenticate `join` with `Authorization: Bearer access-token`.
- [ ] Run the controller test and verify the old internal-auth expectation fails.
- [ ] Remove internal auth configuration from the queue application.
- [ ] Add queue JWT properties and a local `AccessTokenAuthenticationFilter` reading `Authorization`.
- [ ] Run focused queue API tests.

### Task 4: Queue Token and Admission Token Member Binding

**Files:**
- Modify: `ticket-queue/src/main/java/com/ticket/queue/application/QueueTokenClaims.java`
- Modify: `ticket-queue/src/main/java/com/ticket/queue/application/AdmissionTokenIssuer.java`
- Modify: `ticket-queue/src/main/java/com/ticket/queue/application/AdmissionService.java`
- Modify: `ticket-queue/src/main/java/com/ticket/queue/infra/SignedQueueTokenService.java`
- Modify: `ticket-queue/src/main/java/com/ticket/queue/infra/SignedAdmissionTokenIssuer.java`
- Test: `ticket-queue/src/test/java/com/ticket/queue/application/AdmissionServiceTest.java`

- [ ] Update queue tests to expect member id in `QueueTokenClaims` and admission token issuing.
- [ ] Run the focused queue application test and verify it fails.
- [ ] Add `memberId` to `QueueTokenClaims`.
- [ ] Include `memberId` in signed queue tokens.
- [ ] Issue admission tokens with `memberId` as the subject.
- [ ] Run focused queue application and token tests.

### Task 5: Frontend Endpoint Separation

**Files:**
- Modify: `ticket-fe/src/lib/env.ts`
- Modify: `ticket-fe/src/lib/api.ts`
- Modify: `ticket-fe/src/features/booking/api/index.ts`
- Modify: `ticket-fe/src/features/booking/lib/seatSocketClient.ts`
- Create: `ticket-fe/src/features/queue/api/index.ts`

- [ ] Add core, queue, queue-state, and websocket base URL exports with backward-compatible fallback to `NEXT_PUBLIC_API_BASE_URL`.
- [ ] Keep existing `fetchApi` on the core base URL.
- [ ] Add `fetchQueueApi` for queue requests, with 401 refresh retry through core refresh.
- [ ] Update background booking calls and socket URL resolution to use core/ws-specific base URLs.
- [ ] Add queue API helpers for `join`, `enter`, and `getPublicState`.

### Task 6: Verification

**Commands:**
- `ticket-common`: tracked files are deleted and services no longer depend on it.
- `ticket`: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.config.security.JwtTokenServiceTest" --tests "com.ticket.core.config.admission.AdmissionTokenValidatorTest"`
- `ticket-queue`: `./gradlew.bat test --tests "com.ticket.queue.api.AdmissionControllerTest" --tests "com.ticket.queue.application.AdmissionServiceTest" --tests "com.ticket.queue.infra.SignedTokenComponentTest"`
- `ticket-fe`: `pnpm lint`

- [ ] Run the focused backend tests.
- [ ] Run frontend lint.
- [ ] Check `git status --short` across touched repositories.
- [ ] Do not commit.
