-- ShowLike.show가 show module 소유 Show entity에 대한 @ManyToOne에서 scalar showId column으로
-- 바뀌었다(찜이 favorite module로 분리되며 모듈을 넘나드는 JPA 연관관계 금지 규칙을 그대로
-- 적용받았다). DB의 실제 FK CONSTRAINT는 옛 매핑(Hibernate ddl-auto=create가 생성)이 만든 채로
-- 남아있을 수 있다. 이름은 Hibernate의 hash 기반 implicit naming이라 환경마다 달라질 수 있으므로
-- 이름을 하드코딩하지 않고 user_constraints에서 동적으로 찾아 존재할 때만 제거한다. 이미 존재하지
-- 않는 환경에서는 no-op이다. V1과 같은 패턴이다.
--
-- UK_SHOW_LIKES_MEMBER_SHOW(member_id, show_id)는 FOREIGN KEY가 아니라 UNIQUE 제약이라
-- constraint_type = 'R' 필터에 걸리지 않는다 — 그대로 남는다.
DECLARE
    CURSOR fk_constraints IS
        SELECT DISTINCT uc.constraint_name
        FROM user_constraints uc
        JOIN user_cons_columns ucc
          ON ucc.constraint_name = uc.constraint_name
         AND ucc.table_name = uc.table_name
        WHERE uc.table_name = 'SHOW_LIKES'
          AND uc.constraint_type = 'R'
          AND ucc.column_name = 'SHOW_ID';
BEGIN
    FOR fk IN fk_constraints LOOP
        EXECUTE IMMEDIATE
            'ALTER TABLE show_likes DROP CONSTRAINT ' || fk.constraint_name;
    END LOOP;
END;
/
