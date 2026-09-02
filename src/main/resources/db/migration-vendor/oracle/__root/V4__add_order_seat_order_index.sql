DECLARE
    matching_index_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO matching_index_count
    FROM user_ind_columns
    WHERE table_name = 'ORDER_SEATS'
      AND column_name = 'ORDER_ID'
      AND column_position = 1;

    IF matching_index_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX idx_order_seats_order_id ON order_seats (order_id)';
    END IF;
END;
/
