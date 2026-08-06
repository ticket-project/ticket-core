-- Core API 조회 최적화용 Oracle 인덱스
-- 운영은 spring.jpa.hibernate.ddl-auto=none 이므로 엔티티 선언만으로 기존 DB에 반영되지 않는다.

-- 1. 아래 결과가 0건인지 먼저 확인한다.
SELECT performance_id, seat_id, COUNT(*) AS duplicate_count
FROM performance_seats
GROUP BY performance_id, seat_id
HAVING COUNT(*) > 1;

-- 2. USER_IND_COLUMNS에서 같은 컬럼 순서의 인덱스가 이미 있는지 확인한다.
SELECT index_name, table_name, column_name, column_position
FROM user_ind_columns
WHERE table_name IN ('PERFORMANCE_SEATS', 'ORDER_SEATS')
ORDER BY table_name, index_name, column_position;

-- 3. 동일 목적의 인덱스가 없을 때만 실행한다.
-- 좌석 선택 검증의 performance_id + seat_id 조건을 인덱스 탐색으로 처리하고 중복 행을 방지한다.
CREATE UNIQUE INDEX uk_performance_seats_performance_seat
    ON performance_seats (performance_id, seat_id);

-- 주문 상세의 order_seats 조인을 order_id 기준 인덱스 탐색으로 처리한다.
CREATE INDEX idx_order_seats_order_id
    ON order_seats (order_id);
