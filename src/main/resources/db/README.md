# db 마이그레이션 디렉터리 색인

Flyway 소스 경로 색인이다. 상세 정책(baseline 절차, module 개명·소유권 이관 예외, `__root`
규칙)은 [operations.md의 DB 마이그레이션](../../../../docs/operations.md#db-마이그레이션)이
원본이다 — 여기서는 되풀이하지 않는다.

## 디렉터리 의미

- `migration/{module}`: 해당 module이 소유하는 vendor-neutral 공통 SQL(H2/Oracle 동일 문법).
- `migration/__root`: 특정 module 소유가 아닌 vendor-neutral 공통 SQL. 드물어야 한다.
- `migration-vendor/{h2,oracle}/{module}`: 해당 module의 DB별 SQL. 활성 프로파일이 고른
  vendor 경로가 같은 module의 공통 SQL과 함께 적용된다(`application-dev.yml`은 h2,
  `application-prod.yml`은 oracle).

## 버전 번호가 폴더마다 독립적으로 시작하는 이유

`application.yml`의 `spring.modulith.runtime.flyway-enabled: true`로 module마다 독립된
`flyway_schema_history_{module}` 이력 테이블을 쓴다. 그래서 서로 다른 module 폴더에
`V1__...sql`이 여러 개 있어도 충돌이 아니다 — 각 module의 이력은 그 module 폴더 안에서만
순서를 매긴다.

## 공통 SQL vs vendor SQL 판단

- H2와 Oracle에서 DDL이 완전히 같다 -> `migration/{module}`에 한 번만 둔다.
- 문법·타입 등 DDL이 다르다 -> 같은 버전 번호로 `migration-vendor/h2/{module}`과
  `migration-vendor/oracle/{module}` 양쪽에 각각 둔다.

## 새 migration을 어디에 둘지

1. schema 변경을 소유하는 module 패키지를 먼저 정한다(어떤 module에도 속하지 않으면
   `__root`, 드물어야 한다).
2. 버전 번호는 **그 module 폴더 안에서** 다음 번호를 쓴다. 전체 저장소 공용 next-number가
   아니다.
3. 이미 적용된 migration 파일은 수정하지 않는다. 변경이 더 필요하면 새 버전 파일을 추가한다.
