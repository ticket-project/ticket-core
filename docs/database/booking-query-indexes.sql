-- booking의 PERFORMANCE_SEATS/ORDER_SEATS 조회 최적화 인덱스 운영 사전·사후 점검용 Oracle SQL
-- 실제 인덱스 DDL은 Flyway V3, V4 migration이 적용한다.

-- 1. 아래 결과가 0건인지 먼저 확인한다.
SELECT performance_id, seat_id, COUNT(*) AS duplicate_count
FROM performance_seats
GROUP BY performance_id, seat_id
HAVING COUNT(*) > 1;

-- 2. 배포 전후 USER_IND_COLUMNS에서 인덱스 컬럼과 순서를 확인한다.
SELECT index_name, table_name, column_name, column_position
FROM user_ind_columns
WHERE table_name IN ('PERFORMANCE_SEATS', 'ORDER_SEATS')
ORDER BY table_name, index_name, column_position;
