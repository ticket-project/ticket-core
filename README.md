# Ticket Backend

공연/전시 티켓 예매 백엔드다. 인증, 공연 조회, 좌석 선택, 좌석 선점, 주문 시작/취소/만료를
단일 Gradle Spring Boot 프로젝트와 11개 Spring Modulith Application Module로 다룬다.
Application Module(기술 모듈 제외)은 각각 하나의 Bounded Context와 일치한다([ADR 0006](docs/adr/0006-bounded-context-module-boundaries.md)).
대기열 처리는 `ticket-queue` 별도 서버가 담당하고, 이 서버는 Queue Server가 발급한 admission
token을 검증해 예매 API 진입을 제어한다.

결제 도메인과 PG callback, 결제 성공 후 주문 확정은 아직 구현 대상이 아니다.

## 빠른 맥락

- 단일 실행 애플리케이션(`TicketApplication`), 기본 포트 `8080`
- 주 저장소: RDB(로컬 H2 file, 운영 Oracle)와 Redis
- Redis 용도: 좌석 선택, 좌석 hold, refresh token, OAuth2 1회용 code
- 대기열 상태는 이 저장소가 아니라 `ticket-queue`가 관리한다
- 모듈 경계, DAG, cross-module 참조 규칙은 [ADR 0003](docs/adr/0003-spring-modulith-application-module-boundaries.md)이 원본이다

## 문서

작업 규칙과 어떤 문서를 먼저 읽을지는 **[`AGENTS.md`](AGENTS.md)** 하나가 원본이다.

## 로컬 실행

전제: JDK 25, Redis 7, Gradle wrapper.

클론 직후 `bash scripts/link-agent-skills.sh`(Windows는 `scripts\link-agent-skills.cmd`)를 한 번 실행한다.

```powershell
docker run --name ticket-redis -p 6379:6379 -d redis:7
```

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:JWT_SECRET="replace-with-local-32-byte-secret"
$env:JWT_ACCESS_TOKEN_EXPIRATION_SECONDS="1800"
$env:JWT_REFRESH_TOKEN_EXPIRATION_SECONDS="1209600"
$env:ADMISSION_TOKEN_SECRET_KEY="replace-with-shared-admission-secret"
$env:ADMISSION_TOKEN_ENFORCEMENT_ENABLED="false"
$env:GOOGLE_CLIENT_ID="local-google-client-id"
$env:GOOGLE_CLIENT_SECRET="local-google-client-secret"
$env:KAKAO_CLIENT_ID="local-kakao-client-id"
$env:KAKAO_CLIENT_SECRET="local-kakao-client-secret"
$env:KAKAO_ADMIN_KEY="local-kakao-admin-key"

.\gradlew.bat bootRun
```

OAuth2 값은 로컬 기동용 예시다. 실제 소셜 로그인을 확인하려면 각 공급자에서 발급받은
로컬 callback용 값으로 교체한다.

프로파일별 설정과 환경 변수 상세는 [`docs/operations.md`](docs/operations.md)를 본다.

Swagger UI: `/api/swagger-ui.html`, OpenAPI: `/api/api-docs`

## 배포

루트 `Dockerfile`은 `build/libs/*.jar`를 `app.jar`로 복사해 Java 25 JRE 이미지에서
실행한다. 배포 워크플로는 `.github/workflows/deploy.yml`에 있다.

`master` push는 곧 운영 배포다. 절차는 [`docs/operations.md`](docs/operations.md)를 따른다.
