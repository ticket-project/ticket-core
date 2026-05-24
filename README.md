# Ticket Backend

기준일: 2026-05-24

공연/전시 티켓 예매 백엔드다. 인증, 공연 조회, 좌석 선택, 좌석 선점, 주문 시작/취소/만료를 Spring Boot 멀티 모듈 구조로 다룬다. 대기열 처리는 현재 `ticket-queue` 별도 서버로 분리되어 있으며, 이 서버는 Queue Server가 발급한 admission token을 검증해 예매 API 진입을 제어한다.

## 빠른 맥락

- 실행 모듈: `core:core-api`
- 기본 API 포트: `8080`
- 주 저장소: RDB, Redis
- 로컬 DB: `local` 프로파일에서 H2 file DB 사용
- 운영 DB: `prod` 프로파일에서 Oracle 사용
- 대기열 상태 관리: 이 저장소가 아니라 `ticket-queue`
- 대기열 적용 여부 판단: 이 저장소의 `Performance.queueMode`와 `booking-entry` API
- 단기 상태 관리: 좌석 선택, hold, refresh token, OAuth2 1회용 code는 이 저장소의 Redis 사용
- 미구현/후속 범위: 결제 도메인, PG callback, 결제 성공 후 주문 확정/최종 판매 확정

## 먼저 읽을 파일

| 목적 | 파일 |
| --- | --- |
| 전체 작업 규칙 | `AGENTS.md` |
| 현재 기능과 API 흐름 | `docs/development.md` |
| 모듈 책임과 패키지 경계 | `docs/architecture.md` |
| 실행, 프로파일, 검증 | `docs/operations.md` |
| 부하 테스트 진입점 | `docs/load-test.md`, `docs/load-test/` |
| Gradle 모듈 경계 | `settings.gradle` |

## 모듈 구조

```text
ticket
├── core
│   ├── core-api       # Spring Boot 실행, REST/WebSocket, security, controller
│   ├── core-domain    # use case, 도메인 모델, repository, query, port
│   └── core-infra     # Redis/WebSocket/외부 HTTP/AOP/Querydsl 구현
├── storage
│   └── redis-core     # Redis/Redisson 공통 의존성
├── support
│   ├── logging        # 공통 로깅 리소스
│   └── security       # JWT/admission token 공통 보안 유틸
├── load-tests
│   └── gatling        # Gatling 부하 테스트 프로젝트
└── docs               # 개발/아키텍처/운영/부하 테스트 문서
```

## 의존 방향

```text
HTTP/WebSocket
  -> core-api controller/config/security
  -> core-domain command/query/use case
  -> repository 또는 port
  -> core-infra adapter
  -> Redis/RDB/WebSocket/외부 HTTP
```

`core-api`는 진입점과 설정을 담당한다. 비즈니스 규칙과 저장소 직접 접근은 `core-domain` 또는 `core-infra`에 둔다. Redis, Redisson, WebSocket publisher, 외부 HTTP client, AOP 구현은 `core-infra`에 둔다.

## 주요 도메인

| 도메인 | 책임 |
| --- | --- |
| `auth` | 이메일 로그인, JWT refresh, OAuth2 token 교환 |
| `member` | 현재 회원 조회, 회원 탈퇴, 소셜 계정 |
| `show` / `performance` | 공연/전시 목록, 검색, 상세, 회차 조회 |
| `performanceseat` | 좌석 상태 조회, Redis 기반 임시 선택, WebSocket 전파 |
| `hold` | Redis 기반 좌석 선점, hold history |
| `order` | `PENDING` 주문 생성, 조회, 취소, 만료, hold release outbox |
| `queue` | queue mode/level 값과 회차별 admission 진입 검증 연동 |

## 주요 API

| 영역 | Endpoint |
| --- | --- |
| 인증 | `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
| OAuth2 | `GET /api/v1/auth/social/urls`, `POST /api/v1/auth/oauth2/token` |
| 공연 | `GET /api/v1/shows`, `GET /api/v1/shows/{id}`, `GET /api/v1/shows/latest`, `GET /api/v1/shows/search` |
| 회차/좌석 | `GET /api/v1/performances/{performanceId}/summary`, `GET /api/v1/performances/{performanceId}/booking-entry`, `GET /api/v1/performances/{performanceId}/seats/status` |
| 좌석 선택 | `POST /api/v1/performances/{performanceId}/seats/{seatId}/select`, `DELETE /api/v1/performances/{performanceId}/seats/select` |
| 주문 시작 | `POST /api/v1/orders` |
| 주문 조회/취소 | `GET /api/v1/orders/{orderKey}`, `DELETE /api/v1/orders/{orderKey}` |

Swagger UI:

```text
/api/swagger-ui.html
/api/api-docs
```

## 로컬 실행

전제:

- JDK 25
- Redis 7
- Gradle wrapper 사용
- OAuth2/JWT/admission 관련 환경 변수 설정

Redis:

```powershell
docker run --name ticket-redis -p 6379:6379 -d redis:7
```

Spring Boot:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:JWT_SECRET="replace-with-local-32-byte-secret"
$env:JWT_ACCESS_TOKEN_EXPIRATION_SECONDS="1800"
$env:JWT_REFRESH_TOKEN_EXPIRATION_SECONDS="1209600"
$env:ADMISSION_TOKEN_SECRET_KEY="replace-with-shared-admission-secret"

.\gradlew.bat :core:core-api:bootRun
```

OAuth2 로그인을 실제로 확인하려면 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_ADMIN_KEY`도 설정한다.

## 주요 설정

| 파일 | 내용 |
| --- | --- |
| `core/core-api/src/main/resources/application.yml` | 공통 Spring, OAuth2, JWT, admission, Swagger 설정 |
| `core/core-api/src/main/resources/application-local.yml` | H2 file DB, seed 활성화, local redirect |
| `core/core-api/src/main/resources/application-prod.yml` | Oracle datasource, 운영 redirect |
| `storage/redis-core/src/main/resources/redis.yml` | Redis host/port 기본값 |
| `support/logging/src/main/resources/logging.yml` | 로깅 설정 import |

## 검증 명령

작업 범위에 맞게 가장 좁은 명령부터 실행한다.

```powershell
.\gradlew.bat :core:core-api:compileJava
.\gradlew.bat :core:core-domain:test
.\gradlew.bat :core:core-api:test
.\gradlew.bat clean :core:core-api:bootJar -x test
```

모듈 경계나 패키지 의존성을 바꿨다면 ArchUnit 테스트를 먼저 확인한다.

```powershell
.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainArchitectureTest"
.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"
```

## 배포

루트 `Dockerfile`은 `core/core-api/build/libs/*.jar`를 `app.jar`로 복사해 Java 25 JRE 이미지에서 실행한다. GitHub Actions 배포 워크플로는 `.github/workflows/deploy.yml`에 있다.

## AI 작업 메모

- README는 전체 맥락만 담고, 세부 규칙은 `AGENTS.md`와 `docs/`를 우선한다.
- `auth`, `hold`, `order`, `performanceseat`, `queue` 관련 변경은 동시성, 트랜잭션, Redis TTL, 만료 listener/scheduler, admission token 검증을 함께 확인한다.
- 기존 미커밋 변경은 사용자 작업으로 보고 되돌리지 않는다.
- `load-tests/gatling` 실행은 실제 부하를 만들 수 있으므로 사용자가 명시적으로 요청한 경우에만 다룬다.
