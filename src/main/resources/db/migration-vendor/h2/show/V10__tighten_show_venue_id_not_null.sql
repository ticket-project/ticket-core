-- 공연은 공연장을 항상 갖는다. 의도와 배경은 같은 번호의 oracle migration 주석을 본다.
--
-- 값을 임의로 채우지 않는다 -- 잔여 NULL이 있으면 이 ALTER가 실패해 원본을 먼저 확인하게 한다.
-- SHOWS는 pre-Flyway baseline table이라 ShowModuleSlicingSchemaTest처럼 최소 baseline으로 도는
-- 환경에는 컬럼이 없을 수 있다. booking/V3와 같은 형태로 존재할 때만 조인다.

EXECUTE IMMEDIATE COALESCE((
    SELECT 'ALTER TABLE SHOWS ALTER COLUMN VENUE_ID SET NOT NULL'
    FROM DUAL
    WHERE EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = 'SHOWS' AND COLUMN_NAME = 'VENUE_ID' AND IS_NULLABLE = 'YES'
    )
), 'DROP TABLE IF EXISTS __noop_migration_marker__');
