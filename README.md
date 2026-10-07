# Ticket Backend

공연/전시 티켓 예매 백엔드다. 인증, 공연 조회, 좌석 선택, 좌석 선점, 주문 생성/취소/만료를
Spring Modulith Application Module로 나눈 단일 Gradle Spring Boot 프로젝트(`TicketApplication`, 기본 포트
`8080`)로 다룬다. 대기열 처리는 `ticket-queue` 별도 서버가 담당하고, 이 서버는 Queue Server가 발급한
admission token을 검증해 예매 API 진입을 제어한다.

## 로컬 실행

전제: JDK 25, Docker Compose, Gradle wrapper. DB는 로컬·dev·운영 모두 PostgreSQL이다.

```powershell
docker compose -f compose.local.yml up -d postgres
```

환경 변수의 원본은 `src/main/resources/application*.yml`의 `${...}` placeholder다. 기본값이 없는
placeholder는 로컬용 임의 값이라도 설정해야 기동한다. 실제 소셜 로그인을 확인하려면 각 공급자에서 발급받은
로컬 callback용 값을 쓴다.

로컬 DB 기본값은 `jdbc:postgresql://localhost:5433/ticket`, 계정 `ticket`, 비밀번호 `ticket_local`이다.
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`로 덮어쓸 수 있고
`seedLocal`도 같은 값을 사용한다. Redis가 없다면 `docker compose -f compose.local.yml up -d redis`도 실행한다.
PostgreSQL 데이터는 Docker 볼륨에 보존된다. 운영 RDS 전환은 [운영 절차](docs/operations.md)를 따른다.

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun
```

기동은 데이터를 넣지 않는다. 기동이 끝나면 초기 데이터를 넣는다. local 프로파일은 재시작해도 데이터가 남으므로
처음 한 번만 실행하면 된다.

```powershell
.\gradlew.bat seedLocal
```

Swagger UI: `/api/swagger-ui.html`, OpenAPI: `/api/api-docs`

## 배포

`master`에 push하면 `.github/workflows/deploy.yml`이 운영 배포를 시작한다(`paths-ignore` 대상만 바꾼 push는 제외).
`workflow_dispatch`로도 시작할 수 있다.
