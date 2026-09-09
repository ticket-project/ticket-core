-- ShowLike.show가 show module 소유 Show entity에 대한 @ManyToOne에서 scalar showId column으로
-- 바뀌었다(찜이 favorite module로 분리되며 모듈을 넘나드는 JPA 연관관계 금지 규칙을 그대로
-- 적용받았다). DB의 실제 FK CONSTRAINT는 옛 매핑(Hibernate ddl-auto=create가 생성)이 만든 채로
-- 남아있을 수 있다. 이름은 Hibernate의 hash 기반 implicit naming이라 환경마다 달라질 수 있으므로
-- 이름을 하드코딩하지 않고 INFORMATION_SCHEMA에서 동적으로 찾아 존재할 때만 제거한다. 이미
-- 존재하지 않는 환경(신규 H2 스키마 등)에서는 no-op이다. V1과 같은 패턴이다.
--
-- UK_SHOW_LIKES_MEMBER_SHOW(member_id, show_id)는 FOREIGN KEY가 아니라 UNIQUE 제약이라
-- CONSTRAINT_TYPE = 'FOREIGN KEY' 필터에 걸리지 않는다 — 그대로 남는다.
EXECUTE IMMEDIATE COALESCE((
    SELECT 'ALTER TABLE SHOW_LIKES DROP CONSTRAINT "' || kcu.CONSTRAINT_NAME || '"'
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
    JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
        ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
    WHERE kcu.TABLE_NAME = 'SHOW_LIKES' AND kcu.COLUMN_NAME = 'SHOW_ID'
        AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
    FETCH FIRST 1 ROWS ONLY
), 'DROP TABLE IF EXISTS __noop_migration_marker__');
