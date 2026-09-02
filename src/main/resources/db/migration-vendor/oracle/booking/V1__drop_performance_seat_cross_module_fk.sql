-- Task 7이 PerformanceSeat.performance/seat @ManyToOne를 performanceId/seatId scalar column으로
-- 바꿨지만 DB의 실제 FK CONSTRAINT는 옛 매핑(Hibernate ddl-auto=create가 생성)이 만든 채로 남아있을
-- 수 있다. 이름은 Hibernate의 hash 기반 implicit naming이라 환경마다 달라질 수 있으므로 이름을
-- 하드코딩하지 않고 user_constraints에서 동적으로 찾아 존재할 때만 제거한다. 이미 존재하지 않는
-- 환경에서는 no-op이다.
DECLARE
    CURSOR fk_constraints IS
        SELECT DISTINCT uc.constraint_name
        FROM user_constraints uc
        JOIN user_cons_columns ucc
          ON ucc.constraint_name = uc.constraint_name
         AND ucc.table_name = uc.table_name
        WHERE uc.table_name = 'PERFORMANCE_SEATS'
          AND uc.constraint_type = 'R'
          AND ucc.column_name IN ('PERFORMANCE_ID', 'SEAT_ID');
BEGIN
    FOR fk IN fk_constraints LOOP
        EXECUTE IMMEDIATE
            'ALTER TABLE performance_seats DROP CONSTRAINT ' || fk.constraint_name;
    END LOOP;
END;
/
