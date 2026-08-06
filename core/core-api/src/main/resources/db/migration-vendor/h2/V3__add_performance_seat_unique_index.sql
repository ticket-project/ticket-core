CREATE UNIQUE INDEX IF NOT EXISTS uk_performance_seats_performance_seat
    ON performance_seats (performance_id, seat_id);
