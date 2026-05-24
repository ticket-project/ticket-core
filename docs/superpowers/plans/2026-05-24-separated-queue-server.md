# Separated Queue Server Implementation Plan

기준일: 2026-05-24

이 문서는 현재 구현 기준의 확인용 계획이다. 과거 내장 queue token 계획은 더 이상 실행 기준이 아니다.

## 목표

Ticket Server, Queue Server, Gateway, Frontend를 분리된 로그인 후 대기열 구조로 맞춘다.

- Ticket Server: Auth/JWT 발급, 회차별 DIRECT/QUEUE 판단, admission token 검증, 예매 도메인 처리
- Queue Server: enter/status/leave, waiting/active/session Redis 런타임, scheduler 승격, admission token 발급, redirect URL 반환
- Gateway: 단일 외부 도메인에서 `/api/v1/queue/**`, `/api/**`, `/ws/**` 라우팅
- Frontend: `booking-entry` 결과에 따라 direct/queue 분기, queue session polling, ACTIVE 이후 Ticket Server 요청

## 확정 구조

```text
Frontend
  -> Gateway
    -> /api/v1/performances/{performanceId}/booking-entry -> Ticket Server
    -> /api/v1/queue/**                                  -> Queue Server
    -> /api/**                                           -> Ticket Server
    -> /ws/**                                            -> Ticket Server WebSocket
```

Queue Server는 멀티모듈이 아니라 단일 Spring Boot 프로젝트다.

```text
ticket-queue
├── controller
├── service
├── scheduler
├── domain
├── infra
├── config
└── support/security
```

## 구현 체크리스트

### 1. Ticket Server

- [x] 기존 `QueueController` 제거
- [x] 기존 `QueueAdmissionInterceptor`, `RequireQueueAdmission` 제거
- [x] 기존 queue runtime/store/expiration handler 제거
- [x] `QueueMode`, `QueueLevel`은 `Performance` 정책 값으로 유지
- [x] `Performance.requiresQueueAt(now)` 추가
- [x] `GetBookingEntryUseCase` 추가
- [x] `GET /api/v1/performances/{performanceId}/booking-entry` 추가
- [x] `AdmissionTokenValidator`가 회차 정책을 먼저 확인하도록 변경
- [x] 대기열이 필요한 회차에서만 `X-Admission-Token` 검증

### 2. Queue Server

- [x] 별도 `ticket-queue` 폴더에 독립 프로젝트 구성
- [x] `POST /api/v1/queue/performances/{performanceId}/enter`
- [x] `GET /api/v1/queue/performances/{performanceId}/status`
- [x] `POST /api/v1/queue/performances/{performanceId}/leave`
- [x] 최초 enter에서만 access token 검증
- [x] status/leave는 `X-Queue-Session` 기반 처리
- [x] ACTIVE 응답에 `admissionToken`, `redirectUrl` 포함
- [x] WAITING 응답에 `position`, `estimatedWaitSeconds`, `pollAfterSeconds` 포함
- [x] Queue Redis는 waiting/active/session/member state 런타임만 소유
- [x] Queue Server는 회차별 대기열 적용 여부를 판단하지 않음

### 3. Gateway

- [x] Spring Cloud Gateway Server MVC 기반 별도 프로젝트 구성
- [x] `/api/v1/queue/**` -> Queue Server
- [x] `/api/**` -> Ticket Server REST API
- [x] `/ws/**` -> Ticket Server WebSocket
- [x] `Authorization`, `X-Queue-Session`, `X-Admission-Token` 헤더 보존

### 4. Frontend

- [x] 예매 버튼에서 `booking-entry` 호출
- [x] DIRECT면 좌석 화면으로 이동
- [x] QUEUE면 대기열 화면으로 이동
- [x] 대기열 화면에서 enter/status polling 처리
- [x] status polling은 access token 대신 `queueSessionId` 사용
- [x] 서버가 내려준 `pollAfterSeconds` 기준으로 polling 간격 조절
- [x] ACTIVE 이후 admission token 저장 후 redirect URL로 이동
- [x] ACTIVE 이후 Queue Server를 다시 호출하지 않음
- [x] Ticket Server 요청에 access token과 필요한 경우 admission token 전달

## 검증 명령

Ticket Server:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket
.\gradlew.bat :core:core-domain:test
.\gradlew.bat :core:core-api:test
.\gradlew.bat :core:core-api:bootJar -x test
```

Queue Server:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-queue
.\gradlew.bat test
.\gradlew.bat bootJar
```

Gateway:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-gateway
.\gradlew.bat test
.\gradlew.bat bootJar -x test
```

Frontend:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-fe
pnpm build
```

## 남은 운영 검증

- Redis Cluster 환경에서 Queue Redis slot 분산과 polling 부하 확인
- Ticket Redis 단일 노드 latency가 Queue burst와 분리되는지 확인
- Gatling 시나리오가 `queueToken`이 아니라 `queueSessionId`와 `admissionToken`을 사용하는지 확인
- DIRECT 회차와 QUEUE 회차를 각각 end-to-end로 확인