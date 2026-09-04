-- ticket-domain-module-redesign Phase 3 Task 6(ADR 0005): PerformanceSeat entity가
-- performanceGradeId/unitPrice를 매핑하고 @Version 낙관적 락을 쓰도록 바뀌었다. 두 컬럼은 catalog
-- V6가 이미 nullable로 추가했으므로(운영/전체 migration 순서에서는 catalog가 booking보다 먼저
-- 실행된다) 보통은 존재하지만, BookingModuleSlicingSchemaTest·OracleMigrationCompatibilityTest처럼
-- booking 자신의 migration만(또는 catalog와만) 단독 실행되는 환경에서는 아직 없을 수 있다. 그래서
-- 없으면 먼저(nullable로) 만든 뒤 NOT NULL로 조인다. 옛 `price` 컬럼은 이 migration이 전제하지
-- 않는다(일부 검증 baseline에는 그 컬럼조차 없다) — 값이 없으면 0으로 채운다.
--
-- performance_grade_id
EXECUTE IMMEDIATE COALESCE((
    SELECT 'ALTER TABLE PERFORMANCE_SEATS ADD performance_grade_id BIGINT'
    FROM DUAL
    WHERE NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = 'PERFORMANCE_SEATS' AND COLUMN_NAME = 'PERFORMANCE_GRADE_ID'
    )
), 'DROP TABLE IF EXISTS __noop_migration_marker__');

UPDATE PERFORMANCE_SEATS SET performance_grade_id = 0 WHERE performance_grade_id IS NULL;
ALTER TABLE PERFORMANCE_SEATS ALTER COLUMN performance_grade_id SET NOT NULL;

-- unit_price
EXECUTE IMMEDIATE COALESCE((
    SELECT 'ALTER TABLE PERFORMANCE_SEATS ADD unit_price DECIMAL(19, 2)'
    FROM DUAL
    WHERE NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = 'PERFORMANCE_SEATS' AND COLUMN_NAME = 'UNIT_PRICE'
    )
), 'DROP TABLE IF EXISTS __noop_migration_marker__');

UPDATE PERFORMANCE_SEATS SET unit_price = 0 WHERE unit_price IS NULL;
ALTER TABLE PERFORMANCE_SEATS ALTER COLUMN unit_price SET NOT NULL;
ALTER TABLE PERFORMANCE_SEATS ADD CONSTRAINT CK_PERFORMANCE_SEATS_UNIT_PRICE CHECK (unit_price >= 0);

-- @Version 낙관적 락. 이 컬럼은 이번에 처음 추가하므로 존재 여부를 확인할 필요가 없다.
ALTER TABLE PERFORMANCE_SEATS ADD version BIGINT DEFAULT 0 NOT NULL;

CREATE INDEX IDX_PERFORMANCE_SEATS_GRADE ON PERFORMANCE_SEATS (performance_grade_id);
