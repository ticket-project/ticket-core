# DB migration 규칙

Flyway 소스 경로와 migration을 쓰는 규칙의 단일 기준이다. 어떤 프로파일이 어떤 경로를 읽는지는
`application*.yml`의 `spring.flyway.locations`가 원본이다.

## 디렉터리 의미

- `migration-vendor/postgresql/{module}`: local/prod와 테스트의 유일한 스키마 원본이다. 빈 PostgreSQL DB에서
  module별 V1이 현재 스키마를 만들고 이후 변경은 해당 module의 다음 버전으로 추가한다.
- `migration-vendor/postgresql/__root`: 특정 module 소유가 아닌 기술 테이블(Spring Modulith publication
  registry)만 둔다. 드물어야 한다.

PostgreSQL 전환(2026-10-08) 이전의 H2/Oracle migration 이력은 2026-10-17에 저장소에서 지웠다. 필요하면 git
이력에서 본다.

`spring.modulith.runtime.flyway-enabled: true`로 module마다 독립된 `flyway_schema_history_{module}` 이력
테이블을 쓴다. 그래서 서로 다른 module 폴더에 `V1__...sql`이 여러 개 있어도 충돌이 아니다. 각 module의
이력은 그 module 폴더 안에서만 순서를 매긴다.

## 새 migration을 쓸 때

변경은 소유 module의 PostgreSQL migration으로 추가하고 실제 PostgreSQL 테스트로 검증한다.

1. schema 변경을 소유하는 module을 먼저 정한다. 어떤 module에도 속하지 않는 순수 기술 테이블만
   `__root`에 둔다.
2. 버전 번호는 **그 module 폴더 안에서** 다음 번호를 쓴다. 전체 저장소 공용 next-number가 아니다.
3. 이미 적용된 migration 파일은 수정하지 않는다(Flyway checksum 검증이 실패한다). 변경이 더 필요하면
   새 버전 파일을 추가한다.

## 스키마의 원본

**DB 스키마의 원본은 migration이다.** 테이블·컬럼·인덱스·유니크 제약·FK는 migration에만 적고 entity의
`@Table`에는 테이블 이름만 둔다. Hibernate `validate`는 인덱스·유니크 제약을 보지 않아서, entity에 적어도
운영에는 만들어지지 않고 어긋나도 드러나지 않는다. Spring context를 띄우는 테스트도 `@MigratedSchema`로
테스트 PostgreSQL 컨테이너에 같은 migration을 적용해 스키마를 만든다. 빈 DB에서 전체 migration 결과가 entity와
맞는지는 `PostgreSqlMigrationChainSchemaTest`가, module 하나의 migration만으로 그 module 매핑이 맞는지는
`*SlicingSchemaTest`가 확인한다(Docker 필요). slicing 테스트는 booking·payment·venue에만 있다.

- PK·유니크가 아닌 보조 인덱스는 측정으로 필요가 확인된 것만 새 migration으로 만든다.
  `PostgreSqlMigrationChainSchemaTest`가 그 목록을 고정한다.
- 이력 테이블이 없는 폴더는 Spring Modulith가 version `0` baseline을 만든 뒤 가장 낮은 버전(`V1`)부터
  적용한다. `spring.flyway.baseline-on-migrate`/`baseline-version`은 이 동작을 바꾸지 못하므로 두지 않는다
  (`FlywayConfigurationTest`가 고정한다).

## 안전 규칙

- 다른 module 소유 schema를 만들거나 바꾸지 않는다. 소유권 자체가 이관되는 일회성 경우에만 create ->
  backfill -> drop을 한 파일 안에서 원자적으로 수행해 두 module의 migration 실행 순서에 기대지 않게 한다.
- PostgreSQL DDL은 migration 트랜잭션 안에서 실행돼 실패하면 함께 되돌려진다.
- module 식별자 자체가 바뀌어(개명·분리) 이력이 새 `flyway_schema_history_{module}`로 처음부터 다시 실행될
  때만, 새 이력으로 옮겨가는 파일에 존재 확인 가드(멱등화)를 추가할 수 있다. 이미 적용이 끝나 그대로 남는
  이력의 파일은 이 예외 대상이 아니다.
- 어떤 데이터를 가진 컬럼을 조이거나(NOT NULL·CHECK) 이관·삭제할 때는, 해석 불가능한 원본 데이터를 임의로
  지우거나 채우지 말고 제약 위반으로 migration을 실패시켜 원본을 먼저 확인하게 한다.
