-- ticket-domain-module-redesign Phase 2 Task 5(ADR 0005): PERFORMANCE_SEATS에 Task 6이 JPA로 매핑할
-- performance_grade_id/unit_price 컬럼을 nullable로 미리 추가하고, 기존 ShowSeat의 등급 배정과
-- PerformanceSeat.price를 이용해 값을 채운다. PerformanceSeat entity는 아직 이 컬럼을 모른다(Task 6
-- 전) -- NOT NULL 전환과 entity 매핑 자체는 Task 6이 한다. PERFORMANCE_SEATS.price 컬럼은 이번에
-- 지우지 않는다. 컬럼 추가(ALTER)는 SHOW_SEATS 존재 여부와 무관하게 항상 실행한다 -- 컬럼만
-- nullable로 추가하는 것은 안전한 구조 변경이다.
--
-- 매핑 경로(V5가 만든 PERFORMANCE_GRADES까지 필요하므로 V5보다 나중 버전이어야 한다):
--   PERFORMANCE_SEATS(performance_id, seat_id)
--     -> PERFORMANCES.show_id
--       -> SHOW_SEATS(같은 show_id, seat_id) -> SHOW_GRADES(show_grade_id) -> GRADES(grade_code)
--     -> PERFORMANCE_GRADES(같은 performance_id, grade_id)
-- 이 join 경로는 ShowGradePerformanceSeatPriceMismatchQueryTest(Phase 1)가 고정한 경로와 같다.
--
-- SHOW_SEATS/SHOWS는 어떤 Flyway migration도 만들지 않은 pre-Flyway baseline이다.
-- OracleMigrationCompatibilityTest의 legacy baseline은 SHOW_SEATS를 아직 모르는 상태를 재현하므로,
-- backfill UPDATE는 V5와 같은 PL/SQL 패턴으로 SHOW_SEATS 존재 여부를 먼저 확인하고 없으면 no-op이다.
--
-- **알려진 한계**: V5와 같다 -- seed가 이 migration 실행 이후에 넣는 PERFORMANCE_SEATS/SHOW_SEATS는
-- 이 1회성 backfill이 자동으로 반영하지 않는다. ShowGradePerformanceGradeBackfillMigrationTest(H2)가
-- 이 SQL을 재실행해 seed 이후 데이터에도 같은 결과가 나오는지 검증한다.
--
-- **컬럼 추가는 이미 존재하면 건너뛴다**: module별 Flyway는 서로 다른 module 순서로 실행될 수 있고
-- (실측: OracleMigrationCompatibilityTest는 booking을 catalog보다 먼저 적용한다), booking Task 6의
-- 자체 migration(V3, `db/migration-vendor/{h2,oracle}/booking`)이 이 module보다 먼저 실행되면 같은
-- 컬럼을 이미 만들어 뒀을 수 있다. 그래서 두 ADD COLUMN 모두 존재 여부를 먼저 확인한다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCE_SEATS' AND column_name = 'PERFORMANCE_GRADE_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PERFORMANCE_SEATS ADD performance_grade_id NUMBER(19, 0)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCE_SEATS' AND column_name = 'UNIT_PRICE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PERFORMANCE_SEATS ADD unit_price NUMBER(19, 2)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SHOW_SEATS';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE '
            UPDATE PERFORMANCE_SEATS ps
            SET performance_grade_id = (
                    SELECT pg.id
                    FROM PERFORMANCES p
                    JOIN SHOW_SEATS ss ON ss.show_id = p.show_id AND ss.seat_id = ps.seat_id
                    JOIN SHOW_GRADES sg ON sg.id = ss.show_grade_id
                    JOIN GRADES g ON g.code = sg.grade_code
                    JOIN PERFORMANCE_GRADES pg ON pg.performance_id = p.id AND pg.grade_id = g.id
                    WHERE p.id = ps.performance_id
                ),
                unit_price = ps.price
            WHERE ps.performance_grade_id IS NULL';
    END IF;
END;
/
