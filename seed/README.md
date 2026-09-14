# seed — 초기 데이터 적재

애플리케이션 밖에서 도는 **초기 데이터 적재 도구**다. 서버 기동은 데이터를 넣지 않는다.

명령은 둘이고 적재 내용과 순서는 같다. 다른 것은 **접속 설정을 어디서 읽는가**와 **기본값**뿐이다.

| 명령 | 대상 | 접속 설정 원본 | 기본 적재 대상 |
| --- | --- | --- | --- |
| `seedLocal` | 로컬 H2 | `src/main/resources/application-local.yml` | 공용 시드 + 부하 테스트 회원 2,000명 + 부하 회차 8개 |
| `seedProd` | 운영 Oracle | `SPRING_DATASOURCE_*` 환경변수 **뿐** | 공용 시드(공연 데이터)만 |

```text
로컬 : 서버 기동 완료  →  .\gradlew.bat seedLocal  →  개발 · 부하 테스트
운영 : 테이블 준비 완료(배포/Flyway)  →  .\gradlew.bat seedProd
```

**테이블 생성과 데이터 적재는 별개 작업이다.** 이 도구는 어느 쪽에서도 테이블을 만들거나 지우거나
초기화하지 않고, 기존의 불완전한 데이터를 자동으로 고치지도 않는다. `seedProd`는 **이미 준비된
테이블에 데이터를 넣는 명령**이다.

로컬 프로파일은 `ddl-auto: create`다 — **서버를 재시작하면 스키마가 다시 만들어져 데이터가
사라지므로 `seedLocal`을 다시 실행한다.** 이 초기화 정책은 그대로 둔다
(`docs/operations.md`의 프로파일 절 참고).

## 구성

| 경로 | 내용 |
| --- | --- |
| `sql/kopis-curated.sql` | 공용 KOPIS 큐레이티드 시드 데이터 |
| `src/main/java/` | 시드 실행 프로그램(`com.ticket.seed.SeedLocalMain` / `SeedProdMain`, 공통 본체는 `SeedProgram`) |
| `src/test/java/` | 시드 테스트 (`.\gradlew.bat seedTest`) |
| `src/test/resources/sql/` | 테스트용 최소 시드 SQL |
| `kopis/` | KOPIS 수집·검증·날짜 재배치 도구 ([kopis/README.md](kopis/README.md)) |

`seed/src/main/java`는 서비스와 **별개의 Gradle source set**(`seedMain`)이다. 그래서

- 서비스 `bootJar`에 시드 실행 코드와 시드 SQL이 들어가지 않는다
  (`.\gradlew.bat verifySeedNotInBootJar`가 실제 jar를 열어 확인한다),
- `TicketApplication`을 띄우지 않아 웹 서버·Redis·OAuth 설정이 필요하지 않다,
- Spring Modulith가 시드를 업무 모듈로 탐지하지 않는다(`com.ticket.ModularityTests`).

## 사전 조건

1. **테이블이 이미 있어야 한다.** 시드는 테이블을 만들거나 지우지 않는다 — 없으면 없는 테이블
   목록을 출력하고 적재를 시작하기 전에 실패한다. 로컬은 애플리케이션이 만들고(local 프로파일
   `ddl-auto: create`), 운영은 배포(Flyway migration)가 만든다.
2. **접속 설정의 원본이 명령마다 다르다.** 아래 "로컬 실행" / "운영 실행"을 본다.
3. Node/Python은 `kopis/` 도구에만 필요하다. 적재 명령에는 필요하지 않다.

## 로컬 실행

```powershell
.\gradlew.bat seedLocal
```

```bash
./gradlew seedLocal
```

**접속 설정의 원본은 `src/main/resources/application-local.yml`의 `spring.datasource.*` 하나다.**
`seedLocal`이 그 파일을 직접 읽으므로 앱과 시드가 항상 같은 DB를 본다. URL은 `AUTO_SERVER=TRUE`인
H2 파일 DB라 서버가 접속한 상태에서도 같이 붙을 수 있다.

성공하면 작업별 결과를 출력하고 종료 코드 `0`으로 끝난다. 실패하면 원인을 요약하고 `0`이 아닌
코드로 끝난다. 접속 비밀번호는 출력하지 않는다.

## 운영 실행

운영 DB에 넣는 것은 **사람이 자기 PC에서 직접 실행하는 작업**이다. 배포 파이프라인이나 서버
기동이 대신 하지 않는다.

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:oracle:thin:@<tns_alias>"
$env:SPRING_DATASOURCE_USERNAME = "<계정>"
$env:SPRING_DATASOURCE_PASSWORD = "<비밀번호>"
.\gradlew.bat seedProd
```

```bash
SPRING_DATASOURCE_URL=... SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=... ./gradlew seedProd
```

- 변수 이름은 prod 프로파일(`application-prod.yml`)이 쓰는 것과 같다. **세 값 중 하나라도 없으면
  적재를 시작하기 전에 실패한다** — 로컬 프로파일 YAML로 대체하지 않는다. 운영에 넣으려던
  데이터가 조용히 로컬 H2에 들어가는 것이 이 도구에서 가장 나쁜 결과다.
- Wallet(자율운영 DB)을 쓰면 서버와 같은 `TNS_ADMIN`을 그대로 쓴다. Gradle이 환경변수를 실행
  프로세스에 넘기고, `seedProd`는 그 값을 `oracle.net.tns_admin` 시스템 프로퍼티로도 전달한다.
  **Wallet 파일과 실제 비밀번호는 저장소에 넣지 않는다.**
- 확인 범위는 **실제로 접속한 스키마**다(`USER_TABLES` / `USER_TAB_COLUMNS`). 다른 스키마의 동명
  테이블을 준비된 것으로 오인하지 않는다.
- 접속 대상과 작업 결과를 출력하되 비밀번호와 URL 안의 자격증명은 가린다.
- 웹 서버·Redis·OAuth 설정은 필요하지 않다.

### IntelliJ에서 실행 설정 만들기

1. **Run / Debug Configurations → `+` → Gradle**
2. **Run** 칸에 `seedProd`, **Gradle project**는 `ticket`
3. **Environment variables**에 아래 셋(필요하면 `TNS_ADMIN`까지)을 넣는다.

   ```text
   SPRING_DATASOURCE_URL=jdbc:oracle:thin:@<tns_alias>
   SPRING_DATASOURCE_USERNAME=<계정>
   SPRING_DATASOURCE_PASSWORD=<비밀번호>
   TNS_ADMIN=<Wallet 디렉터리 절대경로>
   ```

4. 저장한다. **실행 설정 파일(`.run/*.xml`)을 저장소에 커밋하지 않는다** — 자격증명이 함께
   커밋된다. 이 설정은 각자 자기 PC에만 둔다.

옵션을 함께 주려면 **Run** 칸에 이어 적는다.

```text
seedProd -Dseed.load-test-members.count=100
```

### 운영에 테스트 회원·부하 공연을 추가할 때

운영 기본값은 **공연 데이터만** 적재한다(`seed.load-test-members.count=0`,
`seed.load-test-fixture.performance-count=0`). 필요할 때만 명시해서 켠다.

```powershell
$env:SEED_LOAD_TEST_MEMBER_PASSWORD = "<이번에 쓸 비밀번호>"
.\gradlew.bat seedProd -Dseed.load-test-members.count=100 -Dseed.load-test-fixture.performance-count=2
```

**운영에서 테스트 회원을 만들 때는 `SEED_LOAD_TEST_MEMBER_PASSWORD`가 반드시 있어야 한다.**
없으면 실패한다 — 저장소에 적힌 로컬 기본 비밀번호로 운영 계정을 만들면 그 계정은 사실상 공개
계정이다.

### 적재하는 것

작업은 셋이고 순서가 곧 계약이다(공용 시드가 먼저 `GRADES` 코드를 만들고 부하 픽스처가 그것을
재사용한다). 작업마다 자기 트랜잭션이 있고, 앞 작업이 실패하면 뒤 작업은 실행하지 않는다.

| 작업 | 내용 | `seedLocal` 기본값 | `seedProd` 기본값 |
| --- | --- | --- | --- |
| 공용 시드 | `sql/kopis-curated.sql`의 카테고리·장르·공연자·공연장·공연·회차·좌석·등급·가격·회차좌석·판매정책 | 적재 | 적재 |
| 부하 테스트 픽스처 | 고정 ID 대역(`910000001~`)의 전용 공연 1개, 회차, 전용 물리 좌석(회차당 2,000석) | 회차 8개 | **0개(만들지 않음)** |
| 부하 테스트 회원 | `loadtest{n}@test.com` | 2,000명 | **0명(만들지 않음)** |

부하 테스트 회원의 비밀번호 로컬 기본값은 형제 저장소 `gatling-test`의 `loginPassword` 기본값과
같은 **로컬 전용** 값이다. 바꿀 때는 환경변수로 넘긴다 — 저장소에 새로 적지 않는다.

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
| `seed.jdbc-url` / `seed.jdbc-username` / `seed.jdbc-password` | **검증용 임시 DB에만 쓴다.** 지정하면 로컬 프로파일·환경변수 대신 이 값으로 접속한다 |

비밀번호는 `-D`가 아니라 환경변수로 넘긴다(`SEED_LOAD_TEST_MEMBER_PASSWORD`). `-D`로 넘긴 값은
프로세스 목록과 Gradle 로그에 그대로 남는다.

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

### 커밋 전 관계 검증

개수 비교만으로는 부족하다. 기대 행 수 자체를 "이미 만들어진 좌석"에서 계산하면 **좌석이 아예 없는
공연장은 기대값에서도 빠져** 기대값과 실제값이 사이좋게 0이 된다 — 누락이 정상 적재로 보인다.

그래서 공용 시드는 적재 트랜잭션 안에서, **커밋하기 전에** 관계 자체를 확인한다
(`CuratedSeedVerifier`). 하나라도 어긋나면 예외를 던져 적재 전체를 되돌린다.

- 모든 공용 공연장에 물리 좌석이 있는가
- 모든 공용 회차에 회차좌석이 있는가 / 그 수가 공연장 좌석 수와 같은가
- 회차좌석의 물리 좌석이 그 공연의 공연장에 속하는가
- 회차좌석의 등급이 같은 회차의 `PERFORMANCE_GRADES`이고 단가가 그 등급 가격과 같은가
- 모든 공용 회차에 예매 판매정책이 있는가

### 문장 순서

공용 SQL에는 "실행 시점에 존재하는 행"만 대상으로 삼는 집합 기반 `INSERT ... SELECT`가 둘 있다.
새 데이터는 각각 그 앞에 놓여야 하며, 파일에 마커 주석 두 개로 지점을 표시해 뒀다.

| 마커 | 앞에 놓아야 하는 것 |
| --- | --- |
| `-- @seed-splice: venues` | `PERFORMERS` / `VENUES` / `SHOWS` / `SHOW_GENRES` |
| `-- @seed-splice: performances` | `PERFORMANCES` |

마커 뒤에 선언된 리터럴 INSERT가 있으면 `CuratedSeedStatements`가 **적재를 시작하기 전에** 실패로
만든다. 과거에 이 순서가 깨져 공연장 179개가 좌석을 하나도 받지 못했고, 그 공연장의 공연
198개·회차 662개에 회차좌석이 생성되지 않았다.

### Oracle 날짜 리터럴

실행 직전에 `'2026-06-01'` / `'2026-06-01 14:00:00'` 문자열을 `DATE '...'` / `TIMESTAMP '...'`로
바꾼다. Oracle은 문자열을 날짜로 바꿀 때 세션의 `NLS_DATE_FORMAT`을 쓰므로, 기본 형식이
`DD-MON-RR`인 세션에서는 같은 파일이 다른 결과를 내거나 `ORA-01861`로 실패한다.

## 검증

```powershell
.\gradlew.bat seedTest
```

`test`가 `seedTest`를 함께 돌리므로 CI(`clean test bootJar verifySeedNotInBootJar`)에서도
누락되지 않는다. 서비스 테스트만 좁게 볼 때는 `-x seedTest`로 뺀다.

| 테스트 | 고정하는 것 |
| --- | --- |
| `SeedLocalTest` | 실제 시드 SQL 전체를 실제 앱 스키마(H2)에 적재 → 관계 정합성 → 재실행 멱등성 → 회원 인증 호환성 → 부분 적재 감지 |
| `SeedProdOracleTest` | `seedProd`의 실제 실행 경로를 임시 Oracle에서 통째로 검증 — 환경변수 누락 실패, `USER_TABLES` 기준 스키마 판정, `NLS_DATE_FORMAT` 비의존, 운영 기본값(테스트 회원·부하 회차 0), 옵션 지정 적재, 재실행 멱등성, 부분 적재 감지, 비밀번호 비노출 |
| `SeedSeatCoverageTest` | 좌석 누락 회귀 — 마커 앞에 추가한 공연장은 좌석·회차좌석을 받고, 마커 뒤에 붙이면 적재 전에 실패하며, 좌석 없는 공연장이 남으면 커밋하지 않는다 |
| `SeedFastRunTest` | 실행 순서, 트랜잭션 롤백, 동시 접속, 준비되지 않은 DB 실패, 회원 중복 건너뛰기 |
| `CuratedSeedStatementsTest` | 시드 SQL의 날짜 다변화·회차/판매정책 분리·좌석 복제·회차좌석 불변식 |
| `SeedSettingsTest` | 로컬 접속 설정 원본과 기본값 |
| `SeedProdSettingsTest` | 운영 접속 설정 원본(환경변수)과 기본값, 누락 시 실패, 운영 회원 비밀번호 강제, URL 마스킹, 드라이버 선택 |
| `ServiceSourceSeparationTest` | 시드가 서비스 소스로 다시 섞이지 않는 것 |
| `support.AppSchemaTest` | 테스트 스키마를 실제 앱 entity 매핑으로 만드는 것 |

테스트는 **실제 개발 DB(`~/ticket-local`)도, 실제 운영 DB도 건드리지 않는다.** 임시 디렉터리의 H2
파일 DB와 임시 Oracle 컨테이너에 앱 entity 매핑으로 스키마를 만들어 쓴다. 테스트용 스키마 생성은
테스트 소스(`support.AppSchema`)에만 있다 — 시드 프로그램에는 테이블을 만드는 기능이 없다.

`SeedProdOracleTest`는 Docker가 필요하다(`gvenzl/oracle-free:23-slim`). Docker가 없으면 이 클래스
전체가 건너뛰어진다 — 그때는 **운영 경로가 미검증**이라는 뜻이지 통과가 아니다.

## 시드 데이터 최신화

KOPIS에서 신규 공연을 받아 `sql/kopis-curated.sql`에 누적 추가하는 방법은
[kopis/README.md](kopis/README.md)를 본다. 수집과 DB 적재는 별개 작업이다 — 수집 도구는 SQL
파일만 고치고, DB에 넣는 것은 `seedLocal` / `seedProd`가 한다.

수집 도구는 생성한 블록을 위 두 마커 지점에 나눠 끼워 넣는다. 손으로 추가할 때도 같은 규칙을
따른다 — 파일 끝에 이어 붙이면 좌석 없는 공연장이 생긴다.
