-- PK·유니크가 아닌 보조 인덱스를 당분간 두지 않는다(사용자 결정, 2026-09-29). 필요해지면 새
-- migration으로 다시 만든다. 운영에 이미 있든 없든(검증용 최소 baseline 포함) 통과한다.
-- idx_order_seats_order_id(__root V4)는 H2에서 FK_ORDER_SEATS_ORDER가 쓰는 인덱스라 지울 수 없어
-- 여기서는 뺀다. Oracle 판(oracle/booking/V7)은 이 인덱스도 지운다.
DROP INDEX IF EXISTS IDX_ORDER_SEATS_PERF_SEAT_ID;
DROP INDEX IF EXISTS IDX_PERFORMANCE_SEATS_GRADE;
DROP INDEX IF EXISTS idx_tickets_owner_member_status;
