# DB migration 규칙

Flyway 소스 경로와 migration을 쓰는 규칙의 단일 기준이다. 어떤 프로파일이 어떤 경로를 읽는지는
`application*.yml`의 `spring.flyway.locations`가 원본이다.

## 디렉터리 의미

- `migration-vendor/postgresql/{module}`: 현재 local/prod의 스키마 원본이다. 새 PostgreSQL DB에서
  module별 V1이 현재 스키마를 만들고 이후 변경은 해당 module의 다음 버전으로 추가한다.
  `__root`는 Spring Modulith publication registry만 소유한다. 기존 공통 경로에는 Oracle 문법도 있으므로
  PostgreSQL 프로파일은 이 경로만 읽는다. Oracle/H2의 Flyway 이력은 새 DB에 복사하지 않는다.
- 아래 `migration`과 H2/Oracle 경로는 전환 이전 이력과 테스트용이다. 이미 적용된 파일은 보존한다.

- `migration/{module}`: 해당 module이 소유하는 vendor-neutral 공통 SQL(H2/Oracle 동일 문법).
- `migration/__root`: 특정 module 소유가 아닌 vendor-neutral 공통 SQL. 드물어야 한다.
- `migration-vendor/{h2,oracle}/{module}`: 해당 module의 DB별 SQL. 활성 프로파일이 고른 vendor 경로가
  같은 module의 공통 SQL과 함께 적용된다.

`spring.modulith.runtime.flyway-enabled: true`로 module마다 독립된 `flyway_schema_history_{module}` 이력
테이블을 쓴다. 그래서 서로 다른 module 폴더에 `V1__...sql`이 여러 개 있어도 충돌이 아니다. 각 module의
이력은 그 module 폴더 안에서만 순서를 매긴다.

## 새 migration을 쓸 때

현재 서비스 변경은 PostgreSQL 소유 module에 추가하고 실제 PostgreSQL 테스트로 검증한다.
H2 테스트도 해당 entity를 사용하면 H2 대응 migration을 함께 추가한다. 아래 H2/Oracle 쌍 규칙은
과거 이력에 적용되며, 운영 Oracle 지원을 새로 확장하지 않는다.

1. schema 변경을 소유하는 module을 먼저 정한다. 어떤 module에도 속하지 않는 순수 기술 테이블만
   `__root`에 둔다.
2. 버전 번호는 **그 module 폴더 안에서** 다음 번호를 쓴다. 전체 저장소 공용 next-number가 아니다.
3. 이미 적용된 migration 파일은 수정하지 않는다(Flyway checksum 검증이 실패한다). 변경이 더 필요하면
   새 버전 파일을 추가한다.
4. H2와 Oracle에서 DDL이 완전히 같으면 `migration/{module}`에 한 번만 둔다. 문법·타입이 다르면 같은
   버전 번호로 `migration-vendor/h2/{module}`과 `migration-vendor/oracle/{module}` 양쪽에 각각 둔다. 공통 SQL이
   없고 DB별 SQL만 있는 module은 `migration/{module}` 폴더를 만들지 않는다.

## 스키마의 원본

**DB 스키마의 원본은 migration이다.** 테이블·컬럼·인덱스·유니크 제약·FK는 migration에만 적고 entity의
`@Table`에는 테이블 이름만 둔다. Hibernate `validate`는 인덱스·유니크 제약을 보지 않아서, entity에 적어도
운영에는 만들어지지 않고 어긋나도 드러나지 않는다. Spring context를 띄우는 H2 테스트도 `@MigratedSchema`로
같은 migration을 적용해 스키마를 만든다. 빈 DB에서 전체 migration 결과가 entity와 맞는지는
`MigrationChainSchemaTest`(과거 H2 체인)와 `PostgreSqlMigrationChainSchemaTest`(현재 PostgreSQL, Docker 필요)가 확인한다.

- PK·유니크가 아닌 보조 인덱스는 당분간 두지 않는다. 필요해지면 새 migration으로 만든다.
- `__root`의 `V1`은 Flyway 도입 전 스키마를 빈 DB에 다시 만든다. 운영은 이미 version `1` BASELINE이
  있어 이 파일을 건너뛰고 로컬·테스트 같은 빈 DB만 실행한다. `V1`은 **도입 당시 모양**이라 지금 entity와
  다르고 뒤 migration이 그 모양을 전제로 컬럼·제약을 더한다. 그래서 `V1`을 지금 entity에 맞춰 고치지 않는다.
- 이력 테이블이 없는 폴더는 Spring Modulith가 version `0` baseline을 만든 뒤 가장 낮은 버전(`V1`)부터
  적용한다. `spring.flyway.baseline-on-migrate`/`baseline-version`은 이 동작을 바꾸지 못하므로 두지 않는다
  (`FlywayConfigurationTest`가 고정한다).

## 안전 규칙

- 다른 module 소유 schema를 만들거나 바꾸지 않는다. 소유권 자체가 이관되는 일회성 경우에만 create ->
  backfill -> drop을 한 파일 안에서 원자적으로 수행해 두 module의 migration 실행 순서에 기대지 않게 한다.
- Flyway 도입 전부터 있던(pre-Flyway baseline) 테이블을 만지는 migration은 그 테이블이나 컬럼이 없는 검증
  환경에서 no-op이 되도록 존재 여부를 먼저 확인한다.
- Oracle DDL은 실행 시 암묵적으로 커밋된다. 실패 후 재시도가 필요한 변경(인덱스 등)은 여러 migration으로
  나누고, 각 migration은 같은 목적의 기존 객체가 있으면 건너뛴다.
- module 식별자 자체가 바뀌어(개명·분리) 이력이 새 `flyway_schema_history_{module}`로 처음부터 다시 실행될
  때만, 새 이력으로 옮겨가는 파일에 존재 확인 가드(멱등화)를 추가할 수 있다. 이미 적용이 끝나 그대로 남는
  이력의 파일은 이 예외 대상이 아니다.
- 어떤 데이터를 가진 컬럼을 조이거나(NOT NULL·CHECK) 이관·삭제할 때는, 해석 불가능한 원본 데이터를 임의로
  지우거나 채우지 말고 제약 위반으로 migration을 실패시켜 원본을 먼저 확인하게 한다.
