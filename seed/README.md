# seed — 로컬 초기 데이터

애플리케이션 밖에서 도는 **초기 데이터 적재 도구**다. 서버 기동은 데이터를 넣지 않는다.

```text
서버 기동 완료  →  .\gradlew.bat seedLocal  →  개발 · 부하 테스트
```

로컬 프로파일은 `ddl-auto: create`다 — **서버를 재시작하면 스키마가 다시 만들어져 데이터가
사라지므로 `seedLocal`을 다시 실행한다.** 이 초기화 정책은 그대로 둔다
(`docs/operations.md`의 프로파일 절 참고).

## 구성

| 경로 | 내용 |
| --- | --- |
| `sql/kopis-curated.sql` | 공용 KOPIS 큐레이티드 시드 데이터 |
| `src/main/java/` | 시드 실행 프로그램(`com.ticket.seed.SeedLocalMain`) |
| `src/test/java/` | 시드 테스트 (`.\gradlew.bat seedTest`) |
| `src/test/resources/sql/` | 테스트용 최소 시드 SQL |
| `kopis/` | KOPIS 수집·검증·날짜 재배치 도구 ([kopis/README.md](kopis/README.md)) |

`seed/src/main/java`는 서비스와 **별개의 Gradle source set**(`seedMain`)이다. 그래서

- 서비스 `bootJar`에 시드 실행 코드와 시드 SQL이 들어가지 않는다
  (`.\gradlew.bat verifySeedNotInBootJar`가 실제 jar를 열어 확인한다),
- `TicketApplication`을 띄우지 않아 웹 서버·Redis·OAuth 설정이 필요하지 않다,
- Spring Modulith가 시드를 업무 모듈로 탐지하지 않는다(`com.ticket.ModularityTests`).

## 사전 조건

1. **스키마가 있어야 한다.** 스키마는 애플리케이션이 만든다(local 프로파일 `ddl-auto: create`).
   `seedLocal`은 테이블을 만들거나 지우지 않는다 — 없으면 없는 테이블 목록을 출력하고 실패한다.
2. **접속 설정의 원본은 `src/main/resources/application-local.yml`의 `spring.datasource.*`다.**
   `seedLocal`이 그 파일을 직접 읽으므로 앱과 시드가 항상 같은 DB를 본다.
   URL은 `AUTO_SERVER=TRUE`인 H2 파일 DB라 서버가 접속한 상태에서도 같이 붙을 수 있다.
3. Node/Python은 `kopis/` 도구에만 필요하다. `seedLocal`에는 필요하지 않다.

## 실행

```powershell
.\gradlew.bat seedLocal
```

```bash
./gradlew seedLocal
```

성공하면 작업별 결과를 출력하고 종료 코드 `0`으로 끝난다. 실패하면 원인을 요약하고 `0`이 아닌
코드로 끝난다. 접속 비밀번호는 출력하지 않는다.

### 적재하는 것

| 작업 | 내용 | 기본값 |
| --- | --- | --- |
| 공용 시드 | `sql/kopis-curated.sql`의 카테고리·장르·공연자·공연장·공연·회차·좌석·등급·가격·회차좌석·판매정책 | — |
| 부하 테스트 픽스처 | 고정 ID 대역(`910000001~`)의 전용 공연 1개, 회차, 전용 물리 좌석 | 회차 8개 / 좌석 2,000석 |
| 부하 테스트 회원 | `loadtest{n}@test.com` | 2,000명 |

부하 테스트 회원의 비밀번호 기본값은 형제 저장소 `gatling-test`의 `loginPassword` 기본값과 같은
로컬 전용 값이다. 바꿀 때는 환경변수로 넘긴다 — 저장소에 새로 적지 않는다.

```powershell
$env:SEED_LOAD_TEST_MEMBER_PASSWORD = "..."; .\gradlew.bat seedLocal
```

비밀번호는 앱이 쓰는 것과 같은 `DelegatingPasswordEncoder`로 해싱해 저장하므로, 적재된 회원은
실제 로그인 경로로 그대로 인증된다.

### 조정할 수 있는 값

`-D`로 넘긴다. 지정하지 않으면 위 기본값을 쓴다.

| 프로퍼티 | 설명 |
| --- | --- |
| `seed.load-test-members.count` | 부하 테스트 회원 수 |
| `seed.load-test-fixture.performance-count` | 부하 테스트 전용 회차 수(회차당 좌석 2,000행이 늘어난다) |
| `seed.batch-size` | 공용 시드 batch 크기 |
| `seed.sql-path` | 공용 시드 SQL 경로 |
| `seed.jdbc-url` / `seed.jdbc-username` / `seed.jdbc-password` | **검증용 임시 DB에만 쓴다.** 지정하면 로컬 프로파일 대신 이 값으로 접속한다 |

```powershell
.\gradlew.bat seedLocal -Dseed.load-test-members.count=200
```

## 반복 실행

같은 명령을 여러 번 실행해도 정상적으로 적재된 데이터를 중복 생성하지 않는다. 작업마다 이미
적재됐는지 먼저 판정한다.

- **공용 시드**: 시드 SQL의 리터럴 INSERT 개수에서 테이블별 기대 행 수를 계산해 전부 비교한다.
  전부 비었으면 적재하고, 전부 기대값과 같으면 건너뛴다.
- **부하 테스트 픽스처**: 전용 공연(`shows.id = 910000001`)이 있으면 건너뛴다.
- **부하 테스트 회원**: 이미 있는 이메일만 건너뛰고 없는 것만 만든다.

### 일부만 적재된 상태

**실패로 알리고 멈춘다.** 자동으로 지우거나 채워 넣지 않는다. 어떤 테이블이 기대값과 얼마나
어긋났는지 표로 출력한다.

```text
공용 시드가 일부만 적재된 상태입니다. 자동으로 지우거나 채워 넣지 않습니다.
    SHOW_GENRES                            기대      586 / 실제      300  불일치
필요한 조치
  1. local 프로파일(ddl-auto: create)로 서버를 재기동해 스키마를 다시 만든 뒤
  2. seedLocal을 다시 실행하세요.
```

공용 시드 적재는 하나의 트랜잭션이라 중간에 실패하면 전부 되돌아간다. 앞 작업이 커밋된 뒤 뒤
작업이 실패하면 어디까지 남았는지를 작업별 결과에 그대로 출력한다.

## 검증

```powershell
.\gradlew.bat seedTest
```

`test`가 `seedTest`를 함께 돌리므로 CI(`clean test bootJar verifySeedNotInBootJar`)에서도
누락되지 않는다. 서비스 테스트만 좁게 볼 때는 `-x seedTest`로 뺀다.

| 테스트 | 고정하는 것 |
| --- | --- |
| `SeedLocalTest` | 실제 시드 SQL 전체를 실제 앱 스키마에 적재 → 관계 정합성 → 재실행 멱등성 → 회원 인증 호환성 → 부분 적재 감지 |
| `SeedFastRunTest` | 실행 순서, 트랜잭션 롤백, 동시 접속, 준비되지 않은 DB 실패, 회원 중복 건너뛰기 |
| `CuratedSeedStatementsTest` | 시드 SQL의 날짜 다변화·회차/판매정책 분리·좌석 복제·회차좌석 불변식 |
| `SeedSettingsTest` | 접속 설정 원본과 기본값 |
| `ServiceSourceSeparationTest` | 시드가 서비스 소스로 다시 섞이지 않는 것 |
| `support.AppSchemaTest` | 테스트 스키마를 실제 앱 entity 매핑으로 만드는 것 |

테스트는 **실제 개발 DB(`~/ticket-local`)를 건드리지 않는다.** 임시 디렉터리의 H2 파일 DB에
앱 entity 매핑으로 스키마를 만들어 쓴다.

## 시드 데이터 최신화

KOPIS에서 신규 공연을 받아 `sql/kopis-curated.sql`에 누적 추가하는 방법은
[kopis/README.md](kopis/README.md)를 본다. 수집과 DB 적재는 별개 작업이다 — 수집 도구는 SQL
파일만 고치고, DB에 넣는 것은 `seedLocal`이 한다.
