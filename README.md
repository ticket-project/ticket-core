# Ticket

공연/전시 티켓팅 백엔드 프로젝트다. 순간 트래픽, 대기열, 좌석 선택/선점, 주문 흐름을 Spring Boot 멀티 모듈 구조에서 검증한다.

## 빠른 시작

```bash
docker run --name ticket-redis -p 6379:6379 -d redis:7
./gradlew :core:core-api:bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat :core:core-api:bootRun
```

Swagger:

- `/api/swagger-ui.html`
- `/api/api-docs`

## 문서

- [docs/development.md](docs/development.md)
  - 현재 구현 상태, 주요 API 흐름, 도메인별 개발 기준
- [docs/architecture.md](docs/architecture.md)
  - Gradle 모듈 책임, 패키지 경계, 아키텍처 규칙
- [docs/operations.md](docs/operations.md)
  - 로컬 실행, 검증 명령, 프로파일, 배포 기준
- [docs/load-test.md](docs/load-test.md)
  - 예매 오픈 부하 테스트 진입점
- [AGENTS.md](AGENTS.md)
  - 사람/AI 공통 작업 규칙

## 주요 검증 명령

```bash
./gradlew :core:core-api:compileJava
./gradlew :core:core-domain:test
./gradlew clean :core:core-api:bootJar -x test
```

## 주요 폴더

- `core/core-api`: Spring Boot 실행 모듈, controller, security, WebSocket, HTTP 설정
- `core/core-domain`: use case, 도메인 모델, repository, port
- `core/core-infra`: Redis, WebSocket, 외부 HTTP, AOP 같은 기술 구현
- `storage/redis-core`: Redis 공통 의존성
- `support/logging`: 공통 로깅 설정
- `docs`: 개발, 구조, 운영, 부하 테스트 문서
- `.codex`, `.github`, `.agents`: AI 도구와 자동화 설정

Gatling 부하 테스트 코드는 sibling 저장소 `../ticket-gatling-load-tests/load-tests/gatling`에서 관리한다.

## AI 작업 기준

공통 규칙은 [AGENTS.md](AGENTS.md)를 기준으로 한다. 도구별 설정 파일은 해당 도구가 자동으로 읽기 위한 얇은 어댑터이며, 상세 기준은 `docs/` 문서로 연결한다.
