-- ShowLike.member가 identity Member @ManyToOne에서 scalar memberId column으로 바뀌었지만, DB의
-- 실제 FK CONSTRAINT는 옛 매핑(Hibernate ddl-auto=create가 생성)이 만든 채로 남아있을 수 있다.
-- 이름은 Hibernate의 hash 기반 implicit naming이라 환경마다 달라질 수 있으므로 이름을
-- 하드코딩하지 않고 user_constraints에서 동적으로 찾아 존재할 때만 제거한다. 이미 존재하지 않는
-- 환경에서는 no-op이다. booking의 V1__drop_performance_seat_cross_module_fk.sql과 같은 패턴이다.
DECLARE
    CURSOR fk_constraints IS
        SELECT DISTINCT uc.constraint_name
        FROM user_constraints uc
        JOIN user_cons_columns ucc
          ON ucc.constraint_name = uc.constraint_name
         AND ucc.table_name = uc.table_name
        WHERE uc.table_name = 'SHOW_LIKES'
          AND uc.constraint_type = 'R'
          AND ucc.column_name = 'MEMBER_ID';
BEGIN
    FOR fk IN fk_constraints LOOP
        EXECUTE IMMEDIATE
            'ALTER TABLE show_likes DROP CONSTRAINT ' || fk.constraint_name;
    END LOOP;
END;
/
