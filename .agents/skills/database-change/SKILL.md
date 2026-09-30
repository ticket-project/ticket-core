---
name: database-change
description: ticket-core의 Flyway migration, JPA 매핑, schema·index·constraint 또는 데이터 이관을 변경할 때 사용한다.
---

# DB 변경

## Read Order

1. [AGENTS.md](../../../AGENTS.md) → [DB migration 규칙](../../../src/main/resources/db/README.md)을 읽는다.
2. [ADR 0020](../../../docs/adr/0020-db-schema-source-of-truth-is-migration.md)과 [모듈/aggregate 경계](../../../docs/architecture.md#aggregate-rules)를 확인한다.
3. 해당 module의 공통·H2/Oracle migration, entity·저장 구현과 관련 [migration 테스트](../../../src/test/java/com/ticket/bootstrap/migration/)만 읽는다. 활성 Flyway 경로는 관련 프로파일 설정에서 확인한다.

## Checks

- schema 소유 모듈과 기존 적용 이력을 확인하고 새 migration의 버전·방언 쌍·실행 순서를 DB 규칙에 맞춘다. 적용된 파일은 보존한다.
- migration 결과와 JPA 매핑을 대조한다. Hibernate `validate`만으로 index·unique constraint를 검증했다고 판단하지 않는다.
- 제약 강화·이관·삭제는 기존 데이터, pre-Flyway 환경, Oracle DDL 커밋과 실패 후 재시도 조건을 검토한다. 해석 불가능한 데이터를 임의 보정하지 않는다.
- 다른 모듈 schema·FK로 의존을 만들지 않는다. index 필요성은 관련 쿼리와 측정 근거로 판단하고 [query-performance-review](../query-performance-review/SKILL.md)로 연결한다.

## Verification

[testing.md](../../../docs/testing.md#변경별-검증)에 따라 해당 slicing/변경 테스트와 H2·Oracle 호환 범위를 선택한다. DB 테스트는 운영 migration을 적용하는 `@MigratedSchema` 기준을 따른다. Oracle/Docker 미실행은 미검증으로 보고하며 실행·결과 정리도 testing.md를 따른다.
