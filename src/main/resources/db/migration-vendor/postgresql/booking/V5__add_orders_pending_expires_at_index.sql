-- 주문 만료는 Redis 만료 알림 없이 1초마다 "만료 시각이 지난 PENDING 주문"을 찾아 처리한다(OrderRepository.findExpirable).
-- PENDING 주문만 담은 부분 인덱스라 쌓인 주문 수와 상관없이 만료 대상만 읽는다.
CREATE INDEX idx_orders_pending_expires_at
    ON orders (expires_at)
    WHERE status = 'PENDING';
