-- ticket-domain-module-redesign Phase 3 Task 8(ADR 0005): ShowGrade/ShowSeat(Show 단위 가격·좌석
-- 편성)를 완전히 폐기한다. PerformanceGrade/PerformanceSeat(회차 단위)가 그 책임을 대체했고,
-- Task 5(V5)/Task 6(V6)의 backfill이 이미 끝났다는 전제로 이 migration을 그 뒤 버전으로 둔다.
--
-- SHOW_GRADES/SHOW_SEATS는 어떤 Flyway migration도 만들지 않은 pre-Flyway baseline table이라
-- (docs/operations.md 참고), 이 table 자체가 없는 환경(OracleMigrationCompatibilityTest의 최소
-- baseline 등)에서는 no-op이어야 한다. SHOW_SEATS가 SHOW_GRADES를 참조하므로 먼저 지운다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SHOW_SEATS';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE SHOW_SEATS';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SHOW_GRADES';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE SHOW_GRADES';
    END IF;
END;
/
