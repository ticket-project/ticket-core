-- ticket-domain-module-redesign Phase 3 Task 7(ADR 0005): Order/OrderSeat가 주문 생성 시점의 catalog
-- 표시값을 snapshot으로 남긴다. catalog 값이 나중에 바뀌어도 이미 만든 주문 상세가 바뀌지 않게
-- 하기 위해서다. 동시에 Payment 실패는 더 이상 Order를 끝내는 사건이 아니므로(ADR 0005 결정 5)
-- payment_failed_at 컬럼을 제거한다.
--
-- ORDERS/ORDER_SEATS는 pre-Flyway baseline table이라(docs/operations.md 참고), 이 table 자체가 없는
-- 환경(OracleMigrationCompatibilityTest의 최소 baseline 등)에서는 no-op이어야 한다. ORDER_SEATS는
-- 그 baseline에도 order_id 컬럼 하나짜리로 존재하므로(__root V4가 참조), unique 제약/색인은
-- performance_seat_id 컬럼이 실제로 있을 때만 추가한다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'ORDERS';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ORDERS ADD show_title_snapshot VARCHAR2(1000)';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDERS ADD performance_start_at_snapshot TIMESTAMP';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDERS ADD venue_name_snapshot VARCHAR2(255)';
        EXECUTE IMMEDIATE q'[UPDATE ORDERS SET show_title_snapshot = 'UNKNOWN', performance_start_at_snapshot = created_at, venue_name_snapshot = 'UNKNOWN' WHERE show_title_snapshot IS NULL]';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDERS MODIFY (show_title_snapshot NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDERS MODIFY (performance_start_at_snapshot NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDERS MODIFY (venue_name_snapshot NOT NULL)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'ORDERS' AND column_name = 'PAYMENT_FAILED_AT';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ORDERS DROP COLUMN payment_failed_at';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'ORDER_SEATS';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ORDER_SEATS ADD grade_code_snapshot VARCHAR2(255)';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDER_SEATS ADD grade_name_snapshot VARCHAR2(255)';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDER_SEATS ADD seat_label_snapshot VARCHAR2(500)';
        EXECUTE IMMEDIATE q'[UPDATE ORDER_SEATS SET grade_code_snapshot = 'UNKNOWN', grade_name_snapshot = 'UNKNOWN', seat_label_snapshot = 'UNKNOWN' WHERE grade_code_snapshot IS NULL]';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDER_SEATS MODIFY (grade_code_snapshot NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDER_SEATS MODIFY (grade_name_snapshot NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE ORDER_SEATS MODIFY (seat_label_snapshot NOT NULL)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'ORDER_SEATS' AND column_name = 'PERFORMANCE_SEAT_ID';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ORDER_SEATS ADD CONSTRAINT UK_ORDER_SEATS_ORDER_PERF_SEAT UNIQUE (order_id, performance_seat_id)';
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_ORDER_SEATS_PERF_SEAT_ID ON ORDER_SEATS (performance_seat_id)';
    END IF;
END;
/
