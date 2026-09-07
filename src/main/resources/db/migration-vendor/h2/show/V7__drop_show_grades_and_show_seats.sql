-- ticket-domain-module-redesign Phase 3 Task 8(ADR 0005): ShowGrade/ShowSeat(Show 단위 가격·좌석
-- 편성)를 완전히 폐기한다. PerformanceGrade/PerformanceSeat(회차 단위)가 그 책임을 대체했고,
-- Task 5(V5)/Task 6(V6)의 backfill이 이미 끝났다는 전제로 이 migration을 그 뒤 버전으로 둔다.
--
-- SHOW_GRADES/SHOW_SEATS는 어떤 Flyway migration도 만들지 않은 pre-Flyway baseline table이라
-- (docs/operations.md 참고), 이 table 자체가 없는 환경(OracleMigrationCompatibilityTest의 최소
-- baseline 등)에서는 no-op이어야 한다. SHOW_SEATS가 SHOW_GRADES를 참조하므로 먼저 지운다.
EXECUTE IMMEDIATE COALESCE((
    SELECT 'DROP TABLE SHOW_SEATS'
    FROM DUAL WHERE EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SHOW_SEATS')
), 'DROP TABLE IF EXISTS __noop_migration_marker__');

EXECUTE IMMEDIATE COALESCE((
    SELECT 'DROP TABLE SHOW_GRADES'
    FROM DUAL WHERE EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SHOW_GRADES')
), 'DROP TABLE IF EXISTS __noop_migration_marker__');
