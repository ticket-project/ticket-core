-- 회원·회차당 결제 대기(PENDING) 주문은 하나다. 예전에는 주문 생성 흐름 전체를 회원·회차 분산 락(주문마다 Redis 왕복 3번)으로 줄 세워
-- 지켰다. 락을 걷어내고 DB가 지킨다(gatling-test capacity-log 2026-10-19). V2의 부분 인덱스와 열·조건이 같아 V2를 대신한다.
CREATE UNIQUE INDEX uk_orders_pending_member_performance
    ON orders (member_id, performance_id)
    WHERE status = 'PENDING';

DROP INDEX idx_orders_pending_member_performance;
