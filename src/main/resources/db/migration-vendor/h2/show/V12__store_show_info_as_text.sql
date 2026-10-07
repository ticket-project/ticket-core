-- PostgreSQL TEXT 매핑을 빠른 H2 테스트에서도 VARCHAR JDBC API로 읽고 검증한다.
-- 과거 H2/Oracle migration은 보존하고, H2 테스트 스키마에만 이 변환을 추가한다.
ALTER TABLE IF EXISTS SHOWS ALTER COLUMN IF EXISTS info VARCHAR;
