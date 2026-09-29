-- PK·유니크가 아닌 보조 인덱스를 당분간 두지 않는다(사용자 결정, 2026-09-29). 필요해지면 새
-- migration으로 다시 만든다. idx_order_seats_order_id는 __root V4가 만들었지만 ORDER_SEATS는
-- booking 소유 테이블이라 여기서 지운다. __root V4는 order_id로 시작하는 인덱스가 이미 있으면 만들지
-- 않았으므로 운영에 없을 수 있다 — 이름으로 확인하고 없으면 건너뛴다.
DECLARE
    v_count NUMBER;
BEGIN
    FOR idx IN (
        SELECT 'IDX_ORDER_SEATS_ORDER_ID' AS index_name FROM DUAL
        UNION ALL SELECT 'IDX_ORDER_SEATS_PERF_SEAT_ID' FROM DUAL
        UNION ALL SELECT 'IDX_PERFORMANCE_SEATS_GRADE' FROM DUAL
        UNION ALL SELECT 'IDX_TICKETS_OWNER_MEMBER_STATUS' FROM DUAL
    ) LOOP
        SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = idx.index_name;
        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'DROP INDEX ' || idx.index_name;
        END IF;
    END LOOP;
END;
/
