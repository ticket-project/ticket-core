# Ticket Backend

공연/전시 티켓 예매 백엔드다. 인증, 공연 조회, 좌석 선택, 좌석 선점, 주문 생성/취소/만료를
Spring Modulith Application Module로 나눈 단일 Gradle Spring Boot 프로젝트(`TicketApplication`, 기본 포트
`8080`)로 다룬다. 대기열 처리는 `ticket-queue` 별도 서버가 담당하고, 이 서버는 Queue Server가 발급한
admission token을 검증해 예매 API 진입을 제어한다.

## 서비스 규칙 검토

기획·개발·QA가 테스트 작성 전에 검토할 조건·결과는 [서비스 업무 규칙 초안](docs/service-rules.md)에 있다.
현재 구현과 확정 정책을 구분하며, 미정 정책·Core/Queue/화면 불일치·수정 후보·QA 시나리오는
[서비스 규칙 점검 결과](docs/service-rule-review.md)를 함께 읽는다. 문답으로 확정한 항목은 업무 규칙의
결정 기록에 표시하며, 나머지는 검토 중인 초안이다.

## 로컬 실행

전제: JDK 25, Redis 7, Gradle wrapper.

```powershell
docker run --name ticket-redis -p 6379:6379 -d redis:7
```

환경 변수의 원본은 `src/main/resources/application*.yml`의 `${...}` placeholder다. 기본값이 없는
placeholder는 로컬용 임의 값이라도 설정해야 기동한다. 실제 소셜 로그인을 확인하려면 각 공급자에서 발급받은
로컬 callback용 값을 쓴다.

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
