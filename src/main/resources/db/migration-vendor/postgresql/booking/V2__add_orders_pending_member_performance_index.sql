CREATE INDEX idx_orders_pending_member_performance
    ON orders (member_id, performance_id)
    WHERE status = 'PENDING';
