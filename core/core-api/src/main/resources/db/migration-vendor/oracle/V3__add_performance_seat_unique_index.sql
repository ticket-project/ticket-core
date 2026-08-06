DECLARE
    duplicate_count NUMBER;
    matching_index_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO duplicate_count
    FROM (
        SELECT performance_id, seat_id
        FROM performance_seats
        GROUP BY performance_id, seat_id
        HAVING COUNT(*) > 1
    );

    IF duplicate_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'Duplicate performance_id, seat_id rows exist in PERFORMANCE_SEATS.'
        );
    END IF;

    SELECT COUNT(*)
    INTO matching_index_count
    FROM (
        SELECT c.index_name
        FROM user_ind_columns c
        JOIN user_indexes i
          ON i.index_name = c.index_name
         AND i.table_name = c.table_name
        WHERE c.table_name = 'PERFORMANCE_SEATS'
          AND i.uniqueness = 'UNIQUE'
        GROUP BY c.index_name
        HAVING COUNT(*) = 2
           AND MAX(CASE
                   WHEN c.column_position = 1 AND c.column_name = 'PERFORMANCE_ID' THEN 1
                   ELSE 0
               END) = 1
           AND MAX(CASE
                   WHEN c.column_position = 2 AND c.column_name = 'SEAT_ID' THEN 1
                   ELSE 0
               END) = 1
    );

    IF matching_index_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE UNIQUE INDEX uk_performance_seats_performance_seat '
            || 'ON performance_seats (performance_id, seat_id)';
    END IF;
END;
/
