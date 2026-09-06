# Ticket Backend

공연/전시 티켓 예매 백엔드다. 인증, 공연 조회, 좌석 선택, 좌석 선점, 주문 시작/취소/만료를
단일 Gradle Spring Boot 프로젝트와 Spring Modulith Application Module(`booking`, `catalog`,
`member`, `admission`, `payment`)로 다룬다. 대기열 처리는 `ticket-queue` 별도
서버가 담당하고, 이 서버는 Queue Server가 발급한 admission token을 검증해 예매 API 진입을
제어한다.

결제 도메인과 PG callback, 결제 성공 후 주문 확정은 아직 구현 대상이 아니다.

## 빠른 맥락

- 단일 실행 애플리케이션(`TicketApplication`), 기본 포트 `8080`
- 주 저장소: RDB(로컬 H2 file, 운영 Oracle)와 Redis
- Redis 용도: 좌석 선택, 좌석 hold, refresh token, OAuth2 1회용 code
- 대기열 상태는 이 저장소가 아니라 `ticket-queue`가 관리한다
- 모듈 경계, DAG, cross-module 참조 규칙은 [ADR 0003](docs/adr/0003-spring-modulith-application-module-boundaries.md)이 원본이다

## 문서

이 README는 처음 저장소를 여는 사람을 위한 안내다. 규칙과 상세는 아래가 원본이며,
내용이 갈리면 아래를 따른다.

| 알고 싶은 것 | 문서 |
| --- | --- |
| 작업 규칙, 모듈 판단 기준 요약 | [`AGENTS.md`](AGENTS.md) |
| 모듈 경계, 의존 방향, 새 코드를 어디에 둘지 | [`docs/architecture.md`](docs/architecture.md) |
| 왜 이 구조로 결정했는지(ADR) | [`docs/adr/`](docs/adr/), Modulith 전환은 [ADR 0003](docs/adr/0003-spring-modulith-application-module-boundaries.md) |
| 기능과 API 흐름, 도메인 모델 | [`docs/development.md`](docs/development.md) |
| 주문·hold 트랜잭션과 후처리 | [`docs/core-booking-lifecycle.md`](docs/core-booking-lifecycle.md) |
| 무엇을 검증할지 | [`docs/testing.md`](docs/testing.md) |
| 실행, 프로파일, 마이그레이션, 배포 | [`docs/operations.md`](docs/operations.md) |
| 부하 테스트 | [`docs/load-test.md`](docs/load-test.md), 형제 저장소 `../gatling-test` |

Gradle 프로젝트는 루트 하나뿐이다(`settings.gradle`). 모듈 경계는 Spring Modulith Application
Module이 강제하며, 확정 목록과 허용 의존은 각 모듈 `package-info.java`가 최종 기준이고
`com.ticket.ModularityTests`가 위반을 잡는다.

## 로컬 실행

전제: JDK 25, Redis 7, Gradle wrapper.

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
