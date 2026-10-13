CREATE INDEX idx_performance_seats_reserved
    ON performance_seats (performance_id, seat_id)
    WHERE state = 'RESERVED';
