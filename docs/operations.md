# 운영과 실행 기준

이 문서는 로컬 실행, 검증, 프로파일, 배포 기준을 정리한다.

## 기본 환경

- JDK 25
- Gradle wrapper
- Redis 7
- H2(local)
- Oracle(prod)

## 로컬 실행

Redis 실행:

```bash
docker run --name ticket-redis -p 6379:6379 -d redis:7
```

API 실행:

```bash
./gradlew :core:core-api:bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat :core:core-api:bootRun
```

Swagger:

- `/api/swagger-ui.html`
- `/api/api-docs`

## 빠른 검증

컴파일 확인:

```bash
./gradlew :core:core-api:compileJava
```

도메인/구조 검증:

```bash
./gradlew :core:core-domain:test
```

배포 산출물 기준 검증:

```bash
./gradlew clean :core:core-api:bootJar -x test
```

Windows PowerShell:

```powershell
.\gradlew.bat :core:core-api:compileJava
.\gradlew.bat :core:core-domain:test
.\gradlew.bat clean :core:core-api:bootJar -x test
```

## 프로파일

### local

- H2 file DB
- Redis
- JPA DDL: `create`
- seed data: enabled

관련 설정:

- `core/core-api/src/main/resources/application.yml`
- `core/core-api/src/main/resources/application-local.yml`

### prod

- Oracle driver 사용
- `ddl-auto: none`
- 스키마 변경 시 DDL 또는 마이그레이션 계획이 별도로 필요

관련 설정:

- `core/core-api/src/main/resources/application-prod.yml`

## 배포 workflow

GitHub Actions 배포 workflow는 아래 명령과 맞물린다.

```bash
./gradlew clean :core:core-api:bootJar -x test
```

관련 파일:

- `.github/workflows/deploy.yml`

## 부하 테스트

예매 오픈 부하 테스트는 [load-test.md](load-test.md)에서 시작한다.

상세 실행 문서:

- `docs/load-test/ticket-open-local.md`

Gatling 프로젝트는 sibling 저장소 `ticket-gatling-load-tests`에서 실행한다.

```powershell
cd ..\ticket-gatling-load-tests
.\gradlew.bat -p load-tests/gatling test
.\gradlew.bat -p load-tests/gatling gatlingClasses
```

## 운영 반영 시 주의점

- 운영 DB는 자동 DDL을 사용하지 않는다.
- DB 컬럼명, enum, 상태 모델 변경은 마이그레이션 계획을 함께 작성한다.
- Redis key, TTL, expiration listener 변경은 장애 복구와 scheduler 보정 흐름까지 같이 검토한다.
- 인증/인가 변경은 공개 API 노출 여부와 토큰 만료/재발급 흐름을 함께 확인한다.
