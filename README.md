# Ticket Core

공연·전시 티켓 예매 백엔드다. 공연 조회, 좌석 선택·선점, 주문 생성·취소·만료, 소셜 로그인을 맡는다.
인기 회차의 대기열은 [ticket-queue](https://github.com/ticket-project/ticket-queue)가 맡고, Core는 Queue가 발급한
admission token을 검증해 예매 API 진입을 제어한다. 부하 측정은
[gatling-test](https://github.com/ticket-project/gatling-test)에서 한다.

```text
사용자 ─▶ ticket-queue (대기·입장) ─admission token─▶ ticket-core (좌석·주문) ─▶ PostgreSQL, Redis
                         gatling-test ─▶ 두 서버에 부하를 걸어 Core가 버티는 입장률을 잰다
```

## 다루는 문제

- **같은 좌석을 여러 사람이 동시에 눌러도 선점과 주문은 한 명만 한다.** 좌석 선택(Selection)과 선점(Hold)을 다른
  저장소·다른 수명으로 나누고, 주문은 본인이 선택 중인 좌석으로만 시작한다([ADR 0001](docs/adr/0001-selection-and-hold-are-independent.md),
  [ADR 0021](docs/adr/0021-order-requires-own-selection.md)). 결제는 아직 연결하지 않아 주문은 `PENDING`까지 간다.
- **대기열이 Core를 지키려면 1초에 몇 명을 들여보내야 하는가.** Core가 버티는 입장률을 재서 Queue 설정으로 옮기는
  절차가 [Core 수용량](docs/core-capacity.md)이고, 측정 기록은 gatling-test
  [capacity-log](https://github.com/ticket-project/gatling-test/blob/master/docs/capacity-log/README.md)에 쌓는다.
- **한 애플리케이션 안에서 업무 경계를 지킨다.** Spring Modulith Application Module로 나누고, 모듈 간 의존은 테스트가
  막는다([아키텍처](docs/architecture.md)).

## 빠른 시작

필요한 것: JDK 25, Docker. Gradle은 wrapper가 받는다. 아래는 Windows PowerShell 기준이다(macOS·Linux는
`.\gradlew.bat` 대신 `./gradlew`).

1. PostgreSQL과 Redis를 띄운다. 데이터는 `ticket_postgres-data` 볼륨에 남는다.

   ```powershell
   docker compose -f docker-compose-local.yml up -d postgres redis
   ```

2. 필수 환경 변수를 넣는다. 로컬은 아래 임의 값으로 기동된다(이유는 [설정](#설정)).

   ```powershell
   $env:SPRING_PROFILES_ACTIVE="local"
   $env:JWT_SECRET="local-jwt-secret-key-must-be-32-bytes-or-more"
   $env:JWT_ACCESS_TOKEN_EXPIRATION_SECONDS="1800"
   $env:JWT_REFRESH_TOKEN_EXPIRATION_SECONDS="86400"
   $env:GOOGLE_CLIENT_ID="local"; $env:GOOGLE_CLIENT_SECRET="local"
   $env:KAKAO_CLIENT_ID="local"; $env:KAKAO_CLIENT_SECRET="local"; $env:KAKAO_ADMIN_KEY="local"
   ```

3. 기동한다. 처음 기동 때 Flyway가 테이블을 만든다.

   ```powershell
   .\gradlew.bat bootRun
   ```

4. 다른 터미널에서 확인한다. `"status":"UP"`이 보이면 성공이다.

   ```powershell
   curl.exe -s http://localhost:8080/actuator/health
   ```

5. 초기 데이터를 넣는다. 기동은 데이터를 넣지 않는다. 볼륨에 남으므로 처음 한 번만 하면 된다. 서버를 띄우지 않고
   DB에 바로 넣는 도구라 JWT·소셜 값은 필요 없다. DB 주소를 바꿨다면 같은 `SPRING_DATASOURCE_*` 값을 넣고 실행한다.

   ```powershell
   .\gradlew.bat seedLocal
   ```

API 문서는 Swagger UI `http://localhost:8080/api/swagger-ui.html`, OpenAPI JSON `/api/api-docs`다.

부하 측정처럼 Core도 컨테이너로 띄울 때는 같은 compose 파일을 쓴다. Core가 PostgreSQL·Redis와 같은 Docker 네트워크(`ticket`)에서
이름으로 접속하므로 쿼리가 Docker 포트 중계를 지나지 않는다. 비밀값은 워크스페이스의 `local-env/ticket-core.env`에서 읽고,
Core는 `http://127.0.0.1:8083`에 뜬다.

```powershell
.\gradlew.bat bootJar
docker compose -f docker-compose-local.yml up -d --build --wait
```

## 설정

설정 값의 원본은 `src/main/resources/application*.yml`이다. `${...}` 가운데 기본값이 없는 것은 값을 넣지 않으면
기동이 실패한다.

| 변수 | 로컬 | 운영 값 |
| --- | --- | --- |
| `JWT_SECRET` | 32바이트 이상 아무 문자열 | 서버 환경 파일. 저장소에 없다 |
| `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS`, `JWT_REFRESH_TOKEN_EXPIRATION_SECONDS` | 위 예시 값 | 서버 환경 파일 |
| `GOOGLE_CLIENT_ID`·`_SECRET`, `KAKAO_CLIENT_ID`·`_SECRET`, `KAKAO_ADMIN_KEY` | 아무 값. 소셜 로그인만 안 된다 | 각 공급자 개발자 콘솔 |

- 소셜 로그인을 로컬에서 확인하려면 공급자 콘솔에 `http://localhost:8080/api/v1/auth/oauth2/callback/google`(또는
  `kakao`)을 등록하고 발급받은 값을 쓴다.
- DB·Redis 주소를 바꾸려면 `SPRING_DATASOURCE_URL`·`SPRING_DATASOURCE_USERNAME`·`SPRING_DATASOURCE_PASSWORD`,
  `SPRING_DATA_REDIS_HOST`·`SPRING_DATA_REDIS_PORT`를 넣는다. 5432·6379를 이미 다른 컨테이너가 쓰고 있을 때 필요하다.
  `seedLocal`도 같은 변수를 읽는다.
- 대기열 연동은 기본으로 꺼져 있다(`ADMISSION_TOKEN_ENFORCEMENT_ENABLED=false`). 그래서 로컬은 Queue 없이 예매 API를
  부를 수 있다. 켜면 `ADMISSION_TOKEN_SECRET_KEY`를 ticket-queue와 같은 값으로 맞춘다([운영](docs/operations.md#admission-token-검증)).

## 구조

`com.ticket` 바로 아래 패키지 하나가 Application Module 하나다. 업무 모듈은 `show`·`venue`·`booking`·`payment`·`like`·`member`,
기술 모듈은 `shared`·`security`다. 모듈마다 무엇을 소유하고 왜 그 방향으로만 의존하는지는 [아키텍처](docs/architecture.md)에 있다.

```text
src/main/java/com/ticket/<module>/   endpoint(HTTP) → usecase(트랜잭션) → domain(규칙) ← persistence(저장)
src/main/resources/db/               Flyway migration. 스키마의 원본이다
seed/                                초기 데이터 적재 도구. 서비스 jar에 들어가지 않는다
docs/                                설계·운영 문서와 설계 결정(docs/adr)
```

외부 의존은 PostgreSQL(로컬 Docker, 운영 AWS RDS), Redis(좌석 선택·선점·refresh token), ticket-queue(admission token)다.

| 알고 싶은 것 | 문서 |
| --- | --- |
| 주문 생성·취소·만료가 어떤 순서로 도는가 | [예매 수명주기](docs/core-booking-lifecycle.md) |
| 용어(선택·선점·점유 등)의 뜻 | [용어집](docs/glossary.md) |
| 어떤 테스트를 언제 돌리는가 | [테스트 기준](docs/testing.md) |
| 배포·프로파일·관측·장애 조사 | [운영](docs/operations.md) |
| 이름 짓기·null 계약·조회 구현 | [코드 작성 기준](docs/coding-guidelines.md) |
| 왜 이렇게 설계했는가 | [ADR 색인](docs/adr/README.md) |

## 자주 하는 작업

```powershell
.\gradlew.bat spotlessCheck test   # 서비스 테스트 전체. Testcontainers를 쓰므로 Docker가 필요하다
.\gradlew.bat architectureTest     # 모듈 경계 테스트만
.\gradlew.bat spotlessApply        # 포맷 맞추기
.\gradlew.bat seedTest             # seed 도구 테스트. test에 포함되지 않는다
```

- **스키마 변경**: entity가 아니라 migration 파일을 추가한다. 경로와 규칙은 [migration 규칙](src/main/resources/db/README.md)에 있다.
- **배포**: `master`에 push하면 [deploy.yml](.github/workflows/deploy.yml)이 운영에 배포한다. Markdown·`docs/`·`seed/`만
  바꾼 push는 배포하지 않는다. 절차와 롤백은 [운영](docs/operations.md#배포)에 있다.

## 모르고 바꾸면 사고가 나는 것

- **`spring.jpa.open-in-view: false`**: 켜면 요청이 첫 DB 접근부터 응답 끝까지 DB 연결을 쥔다. 그러면 Core가 받는 사용자
  수가 연결 풀 크기에 묶인다([예매 수명주기](docs/core-booking-lifecycle.md#지켜야-할-원칙)).
- **스키마는 migration이 만들고 Hibernate는 `validate`만 한다**: entity만 고치면 기동이 실패한다. 이미 적용된 migration
  파일은 고치지 않는다([ADR 0020](docs/adr/0020-db-schema-source-of-truth-is-migration.md)).
- **`booking.OrderCreated`·`OrderTerminated`의 패키지**: 클래스 이름이 이벤트 기록 테이블에 저장된다. 옮기면 처리 못 한
  이벤트가 재처리되지 않는다.

## 기여

커밋·PR·Issue 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있다. 커밋 제목은 `<type>(<scope>): <한국어 설명>`이다.
미해결 결함과 제안은 [Issues](https://github.com/ticket-project/ticket-core/issues)에서 추적한다.

AI 코딩 에이전트용 지침은 [AGENTS.md](AGENTS.md)다. `CLAUDE.md`는 이 파일을 불러오기만 한다.
