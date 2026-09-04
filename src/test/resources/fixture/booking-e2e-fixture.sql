-- 예매 흐름 E2E 테스트용 최소 데이터. 좌석 4개짜리 회차 하나.
--
-- ID 대역: 920000001~ 을 쓴다. LoadTestFixtureSeeder(910000000~)와 겹치지 않는다.
-- 컬럼 목록은 core-infra의 LoadTestFixtureSeeder를 따른다. 그쪽 INSERT문이 같은 H2 + Hibernate
-- 생성 스키마에서 실제로 도는 참조 구현이다.
--
-- 시각은 CURRENT_TIMESTAMP 기준 상대값으로 쓴다. 고정 날짜를 박으면 언젠가 판매 기간을 벗어나
-- 테스트가 조용히 깨진다.

INSERT INTO venues (
  id, name, address, region, address_detail, zip_code,
  latitude, longitude, phone, image_url,
  view_box_width, view_box_height, seat_diameter, gap_x, gap_y,
  created_at, created_by
) VALUES (
  920000001, '[E2E] 예매 흐름 검증 공연장', '테스트 주소', 'SEOUL', '상세', '00000',
  37.5, 127.0, '000-0000-0000', NULL,
  500, 356, 6.0, 9.0, 8.0,
  CURRENT_TIMESTAMP, 'BOOKING_E2E'
);

INSERT INTO shows (
  id, title, sub_title, info,
  start_date, end_date, view_count, sale_type,
  sale_start_date, sale_end_date, image,
  venue_id, running_minutes, performer_id,
  created_at, created_by
) VALUES (
  920000001, '[E2E] 예매 흐름 검증 공연', '통합 테스트 전용', '실제 판매하지 않는 검증용 데이터',
  DATEADD('DAY', 60, CURRENT_DATE), DATEADD('DAY', 90, CURRENT_DATE),
  0, 'GENERAL',
  DATEADD('DAY', -1, CURRENT_TIMESTAMP), DATEADD('DAY', 30, CURRENT_TIMESTAMP), NULL,
  920000001, 120, NULL,
  CURRENT_TIMESTAMP, 'BOOKING_E2E'
);

INSERT INTO seats (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by) VALUES
  (920000001, 920000001, 'SEC-01', 'ROW-01', '01', 1, 20.0, 20.0, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000002, 920000001, 'SEC-01', 'ROW-01', '02', 1, 29.0, 20.0, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000003, 920000001, 'SEC-01', 'ROW-01', '03', 1, 38.0, 20.0, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000004, 920000001, 'SEC-01', 'ROW-01', '04', 1, 47.0, 20.0, CURRENT_TIMESTAMP, 'BOOKING_E2E');

INSERT INTO show_grades (id, show_id, grade_code, grade_name, price, sort_order, created_at, created_by)
VALUES (920000001, 920000001, 'R', 'R석', 120000, 1, CURRENT_TIMESTAMP, 'BOOKING_E2E');

INSERT INTO show_seats (id, show_id, seat_id, show_grade_id, created_at, created_by) VALUES
  (920000001, 920000001, 920000001, 920000001, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000002, 920000001, 920000002, 920000001, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000003, 920000001, 920000003, 920000001, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000004, 920000001, 920000004, 920000001, CURRENT_TIMESTAMP, 'BOOKING_E2E');

-- order_open_time < now < order_close_time 이어야 주문이 열린다.
-- hold_time을 넉넉히 두어 스케줄러 만료가 테스트 중에 끼어들지 않게 한다.
INSERT INTO performances (
  id, show_id, performance_no, start_time, end_time,
  order_open_time, order_close_time, max_can_hold_count, hold_time,
  created_at, created_by
) VALUES (
  920000001, 920000001, 1,
  DATEADD('DAY', 61, CURRENT_TIMESTAMP), DATEADD('DAY', 61, CURRENT_TIMESTAMP),
  DATEADD('DAY', -1, CURRENT_TIMESTAMP), DATEADD('DAY', 30, CURRENT_TIMESTAMP),
  2, 600,
  CURRENT_TIMESTAMP, 'BOOKING_E2E'
);

-- ticket-domain-module-redesign Phase 3 Task 6(ADR 0005): PerformanceSeat.unitPrice의 원본은
-- PerformanceGrade.price다. GRADES는 code당 하나만 있어야 해서 이미 있으면 재사용한다.
MERGE INTO grades g
USING (SELECT 920000001 AS id, 'R' AS code, 'R석' AS name) src
ON (g.code = src.code)
WHEN NOT MATCHED THEN INSERT (id, code, name, created_at, created_by)
  VALUES (src.id, src.code, src.name, CURRENT_TIMESTAMP, 'BOOKING_E2E');

INSERT INTO performance_grades (id, performance_id, grade_id, price, sort_order, created_at, created_by)
VALUES (
  920000001, 920000001, (SELECT id FROM grades WHERE code = 'R'), 120000, 1,
  CURRENT_TIMESTAMP, 'BOOKING_E2E'
);

-- FORCE_OFF: 대기열 없이 Core를 직접 호출하는 회차. admission token 검증 경로를 타지 않는다.
INSERT INTO performance_queue_policies (
  performance_id, queue_mode, queue_level, preopen_queue_start_at,
  waiting_room_message, reason, created_at, created_by
) VALUES (
  920000001, 'FORCE_OFF', 'LEVEL_1', NULL,
  '통합 테스트 전용', '대기열 없이 Core를 직접 검증하는 회차', CURRENT_TIMESTAMP, 'BOOKING_E2E'
);

INSERT INTO performance_seats (
  id, performance_id, seat_id, state, performance_grade_id, unit_price, version, created_at, created_by
) VALUES
  (920000001, 920000001, 920000001, 'AVAILABLE', 920000001, 120000, 0, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000002, 920000001, 920000002, 'AVAILABLE', 920000001, 120000, 0, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000003, 920000001, 920000003, 'AVAILABLE', 920000001, 120000, 0, CURRENT_TIMESTAMP, 'BOOKING_E2E'),
  (920000004, 920000001, 920000004, 'AVAILABLE', 920000001, 120000, 0, CURRENT_TIMESTAMP, 'BOOKING_E2E');
