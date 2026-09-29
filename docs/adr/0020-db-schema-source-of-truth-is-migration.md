# ADR 0020: DB 스키마의 원본은 migration이다 — entity `@Table`에는 테이블 이름만 둔다

## 상태

채택됨 (2026-09-29)

## 배경

스키마를 만드는 곳이 둘이었다.

- 운영(Oracle)은 Flyway migration으로 만들고, Hibernate는 `ddl-auto: validate`로 확인만 한다.
- local 프로파일과 대부분의 통합 테스트는 Hibernate `ddl-auto: create`로 entity에서 만들었다.

Hibernate `validate`는 테이블·컬럼·타입만 본다. 인덱스·유니크 제약·FK·`nullable`·길이는 보지 않는다.
그래서 entity와 migration이 어긋나도 아무 데서도 드러나지 않았다.

- `ORDER_HOLD_RELEASE_PROGRESS`는 entity에만 있고 migration이 없었다. local과 테스트는 통과했고, 운영
  기동에서야 실패했다.
- `ORDER_SEATS`의 유니크 제약 이름이 entity(`UK_ORDER_SEATS_ORDER_PERFORMANCE_SEAT`)와
  migration(`UK_ORDER_SEATS_ORDER_PERF_SEAT`)에서 달랐다.
- entity의 `@Index`는 운영에 만들어지지 않는데도 인덱스가 있는 것처럼 보였다.

## 검토한 대안

1. **entity를 원본으로 둔다(`ddl-auto`로 운영까지).** 운영 데이터가 있는 DB에서 Hibernate 자동 DDL은
   컬럼 삭제·타입 변경·backfill을 다루지 못한다. 채택하지 않는다.
2. **둘 다 적는다.** entity에 인덱스·유니크를 적고 migration에도 적는다. 두 곳을 손으로 맞춰야 하고
   `validate`가 어긋남을 잡지 못한다. 지금까지의 상태이고, 위 문제들이 여기서 생겼다.
3. **migration만 원본으로 둔다.** local과 테스트도 migration으로 스키마를 만들고, entity에는 매핑에
   필요한 것만 남긴다. 채택한다.

## 결정

1. **DB 스키마의 원본은 `src/main/resources/db/migration/`이다.** 방언이 다른 SQL은 같은 버전 규칙으로
   `db/migration-vendor/{h2,oracle}/`에 둔다. 테이블·컬럼·인덱스·유니크 제약·FK는 migration에만 적는다.
2. **entity의 `@Table`에는 테이블 이름만 둔다.** `indexes`, `uniqueConstraints`를 적지 않는다.
3. **local·dev는 Flyway로 스키마를 만들고 `validate`로 확인한다.** 빈 DB는 `__root` V1
   (`V1__create_pre_flyway_baseline_schema.sql`, Flyway 도입 전 스키마)부터 적용한다. 운영 root 이력에는
   version `1` BASELINE이 있어 V1을 건너뛴다.
4. **Spring context를 띄우는 H2 테스트도 migration으로 스키마를 만든다**(`@MigratedSchema`). context마다
   H2를 비우고 모든 module migration을 운영 순서로 적용한다. 좋아요·이메일 중복처럼 DB 유니크 제약에
   기대는 동작이 운영과 같은 스키마에서 검증된다.
5. **빈 DB에서 전체 migration이 entity 매핑과 맞는지**는 `MigrationChainSchemaTest`(H2)와
   `OracleMigrationChainSchemaTest`(Oracle, Docker 필요)가 확인한다. entity만 바꾸고 migration을 빠뜨리면
   여기서 실패한다.

## 결과

- 인덱스·유니크 제약을 바꾸려면 새 migration 파일을 추가한다. entity를 고쳐도 스키마는 바뀌지 않는다.
- `validate`가 보지 않는 항목(인덱스·유니크·FK)은 migration 테스트가 필요하면 직접 확인한다.
- `@Column`의 `unique`, `nullable`, `length`는 이번 결정 범위 밖이라 남아 있다. 같은 이유로 스키마에는
  영향이 없다.
- seed 테스트(`seed/src/test`의 `AppSchema`)는 아직 entity로 스키마를 만든다. 유니크 제약이 없는
  스키마라서, 중복 거절에 기대는 시드 검증을 추가할 때는 migration으로 옮긴다.
- H2의 Oracle 호환 모드는 `DATE`를 `TIMESTAMP(0)`으로 저장한다. 그래서 H2 `validate`는
  `H2OracleModeDialect`를 쓴다.
- 절차와 확인 방법은 [operations.md의 DB 마이그레이션](../operations.md#db-마이그레이션)이 원본이다.
