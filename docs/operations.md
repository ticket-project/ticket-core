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
- Flyway: disabled
- seed data: enabled

관련 설정:

- `core/core-api/src/main/resources/application.yml`
- `core/core-api/src/main/resources/application-local.yml`

### prod

- Oracle driver 사용
- `ddl-auto: none`
- Flyway: enabled
- 기존 운영 스키마는 최초 도입 시 Flyway baseline으로 등록

관련 설정:

- `core/core-api/src/main/resources/application-prod.yml`

## DB 마이그레이션

Flyway는 `core:core-api` 실행 모듈에서만 사용한다. 마이그레이션 파일 위치는 아래 경로다.

```text
core/core-api/src/main/resources/db/migration
```

운영 DB는 이미 테이블이 존재한다는 전제로 도입한다. 최초 반영 전에는 다음 순서를 지킨다.

1. 운영 DB 백업 또는 복구 지점을 확보한다.
2. 애플리케이션 DB 계정이 `flyway_schema_history` 테이블을 생성하고 이후 DDL을 실행할 권한이 있는지 확인한다.
3. 최초 도입 배포에서만 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`를 설정한다.
4. 애플리케이션 기동 후 `flyway_schema_history`에 version `1` baseline 기록이 생성됐는지 확인한다.
5. baseline 확인 후에는 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`로 되돌리거나 환경 변수를 제거한다.

기존 운영 스키마를 다시 만드는 `V1__...sql`은 추가하지 않는다. 이후 테이블 구조 변경은 새 파일로만 추가한다.

```text
V2__add_payment_tables.sql
V3__add_order_confirmed_at.sql
```

이미 운영에 적용된 migration 파일은 수정하지 않는다. 변경이 더 필요하면 다음 버전 파일을 새로 만든다.

local 프로파일은 H2 file DB와 Hibernate `ddl-auto:create`, seed loader를 유지한다. 따라서 local에서는 Flyway가 비활성화되어 있고, 운영 마이그레이션 검증은 Oracle 호환 DB나 별도 검증 환경에서 수행한다.

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

Gatling 프로젝트는 루트 Gradle wrapper로 실행한다.

```powershell
.\gradlew.bat -p load-tests/gatling test
.\gradlew.bat -p load-tests/gatling gatlingClasses
```

## 운영 반영 시 주의점

- 운영 DB는 자동 DDL을 사용하지 않는다.
- DB 컬럼명, enum, 상태 모델 변경은 마이그레이션 계획을 함께 작성한다.
- Redis key, TTL, expiration listener 변경은 장애 복구와 scheduler 보정 흐름까지 같이 검토한다.
- 인증/인가 변경은 공개 API 노출 여부와 토큰 만료/재발급 흐름을 함께 확인한다.
