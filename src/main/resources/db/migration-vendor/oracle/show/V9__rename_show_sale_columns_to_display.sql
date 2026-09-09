-- ADR 0007: Show의 판매 필드는 표시 전용이라는 사실을 컬럼 이름에도 반영한다. 실제 주문 접수
-- 가능 여부는 booking의 BOOKING_PERFORMANCE_SALES_POLICIES가 회차 단위로 판단하고,
-- 이 컬럼들은 목록·검색·상세 표시에만 쓰인다.
--
-- SHOWS는 어떤 Flyway migration도 만들지 않은 pre-Flyway baseline table이다(docs/operations.md
-- 참고). local(ddl-auto=create) 환경에서는 Hibernate가 이미 새 컬럼명으로 만들어 두므로 이
-- migration은 그 환경에서 no-op이어야 한다 — 컬럼마다 "아직 옛 이름인가"를 확인하고 아니면
-- 손대지 않는다.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SHOWS' AND column_name = 'SALE_TYPE';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SHOWS RENAME COLUMN SALE_TYPE TO DISPLAY_SALE_TYPE';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SHOWS' AND column_name = 'SALE_START_DATE';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SHOWS RENAME COLUMN SALE_START_DATE TO DISPLAY_SALE_STARTS_AT';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SHOWS' AND column_name = 'SALE_END_DATE';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SHOWS RENAME COLUMN SALE_END_DATE TO DISPLAY_SALE_ENDS_AT';
    END IF;
END;
/
