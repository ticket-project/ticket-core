-- PK·유니크가 아닌 보조 인덱스를 당분간 두지 않는다(사용자 결정, 2026-09-29). 필요해지면 새
-- migration으로 다시 만든다. order_id 조회는 uk_payments_order_attempt(order_id, attempt_no)가 남아 받친다.
DROP INDEX IF EXISTS idx_payments_order_status;
