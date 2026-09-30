---
name: query-performance-review
description: ticket-core의 JPA·Querydsl·SQL 조회에서 N+1, index, pagination, query 수 또는 트랜잭션/connection 점유 성능을 검토할 때 사용한다. 부하 실행은 별도 요청 범위다.
---

# 조회 성능 검토

## Read Order

1. [AGENTS.md](../../../AGENTS.md) → [조회 코드 기준](../../../docs/coding-guidelines.md#조회-코드)과 [Module Structure](../../../docs/architecture.md#module-structure)를 읽는다.
2. 해당 use case → persistence 조회 → migration의 index/constraint → 실제 조회 테스트 순으로 좁게 읽는다.
3. [ArchitectureRulesTest](../../../src/test/java/com/ticket/ArchitectureRulesTest.java)의 승인된 조회 공개면을 확인한다. DB 변경은 [migration 규칙](../../../src/main/resources/db/README.md), 관측은 [Core 용량 관측](../../../docs/operations.md#core-용량-관측)을 필요할 때 읽는다.

## Checks

- SQL/query 수·조회량·lazy 접근·페이지 경계·connection 점유 중 병목 가설과 관측 근거를 구분한다. H2 결과를 Oracle 실행계획·운영 성능으로 일반화하지 않는다.
- WHERE·join·GROUP BY/DISTINCT·ORDER BY·null·LIKE escaping·커서 tie breaker를 전후 대조해 정확성을 먼저 보존한다.
- 저장 구현은 자기 모듈 데이터를 조회하고 다른 모듈 결과 조합은 use case의 공개 API에서 한다. N+1 해소를 다른 모듈 entity 직접 join으로 해결하지 않는다.
- 자기 모듈의 승인된 조회 Repository 호출을 불필요한 1:1 port/adapter로 감싸지 않는다. 새 조회 공개면은 구조 테스트와 [modulith-boundaries](../modulith-boundaries/SKILL.md)를 대조한다.
- index·schema 변경은 [database-change](../database-change/SKILL.md), 성능 측정용 부하 실행은 명시 요청 시 [loadtest](../loadtest/SKILL.md)를 따른다.

## Verification

검토만 했다면 확인한 코드·SQL·측정 근거와 미확인 가설을 보고한다. 쿼리를 수정했다면 관련 persistence/조회 테스트로 결과·정렬·페이지·query 수를 확인하고 구조 영향에 따라 넓힌다. [testing.md](../../../docs/testing.md#변경별-검증)와 [verify](../verify/SKILL.md)를 따른다.
