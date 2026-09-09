-- Show.venue(@ManyToOne)가 venue module 소유 Venue entity에 대한 연관관계에서 scalar venue_id
-- column으로 바뀌었다(물리 공연장·좌석이 venue module로 분리되며 모듈을 넘나드는 JPA 연관관계
-- 금지 규칙을 그대로 적용받았다). DB의 실제 FK CONSTRAINT는 옛 매핑(Hibernate ddl-auto=create가
-- 생성)이 만든 채로 남아있을 수 있다. 이름은 Hibernate의 hash 기반 implicit naming이라 환경마다
-- 달라질 수 있으므로 이름을 하드코딩하지 않고 INFORMATION_SCHEMA에서 동적으로 찾아 존재할 때만
-- 제거한다. 이미 존재하지 않는 환경(신규 H2 스키마 등)에서는 no-op이다. like/V1·V2(옛 favorite)와 같은 패턴이다.
EXECUTE IMMEDIATE COALESCE((
    SELECT 'ALTER TABLE SHOWS DROP CONSTRAINT "' || kcu.CONSTRAINT_NAME || '"'
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
    JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
        ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
    WHERE kcu.TABLE_NAME = 'SHOWS' AND kcu.COLUMN_NAME = 'VENUE_ID'
        AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
    FETCH FIRST 1 ROWS ONLY
), 'DROP TABLE IF EXISTS __noop_migration_marker__');
