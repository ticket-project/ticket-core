CREATE INDEX IF NOT EXISTS idx_order_seats_order_id
    ON order_seats (order_id);
