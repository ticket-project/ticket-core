-- ============================================================
-- 시드 테스트용 최소 SQL. seed/sql/kopis-curated.sql과 같은 구조(리터럴 INSERT + 뒤쪽 집합 기반
-- INSERT ... SELECT)를 유지하되 행 수만 줄였다.
--
-- 100만 행에 가까운 실제 시드는 SeedLocalTest가 한 번만 돌린다. 여기 파일은 실행 경로·완전성
-- 판정·동시 접속처럼 데이터 양과 무관한 것을 빠르게 확인할 때 -Dseed.sql-path로 지정한다.
-- 컬럼 목록은 실제 시드와 같아야 한다 -- 회차/판매정책 분리 정규식이 그 형식을 본다.
-- ============================================================

INSERT INTO CATEGORIES (id, name, code, created_at, created_by) VALUES (1, '콘서트', 'CONCERT', '2026-01-01 10:00:00', '시드');

INSERT INTO GENRES (id, category_id, name, code, created_at, created_by) VALUES (1, 1, '클래식', 'CLASSICAL', '2026-01-01 10:00:00', '시드');

INSERT INTO PERFORMERS (id, name, profile_image_url, created_at, created_by) VALUES (1, '테스트 공연자', NULL, '2026-01-01 10:00:00', '시드');

INSERT INTO VENUES (id, name, address, region, address_detail, zip_code, latitude, longitude, phone, image_url, view_box_width, view_box_height, seat_diameter, gap_x, gap_y, created_at, created_by) VALUES (1, '테스트 공연장 1', '서울특별시', 'SEOUL', '1관', '00001', 37.5, 127.0, '02-000-0000', NULL, 500, 356, 4.8, 2.5, 2.5, '2026-01-01 10:00:00', '시드');
INSERT INTO VENUES (id, name, address, region, address_detail, zip_code, latitude, longitude, phone, image_url, view_box_width, view_box_height, seat_diameter, gap_x, gap_y, created_at, created_by) VALUES (2, '테스트 공연장 2', '서울특별시', 'SEOUL', '2관', '00002', 37.5, 127.0, '02-000-0000', NULL, 500, 356, 4.8, 2.5, 2.5, '2026-01-01 10:00:00', '시드');

INSERT INTO SHOWS (id, title, sub_title, info, start_date, end_date, view_count, display_sale_type, display_sale_starts_at, display_sale_ends_at, image, venue_id, running_minutes, performer_id, created_at, created_by) VALUES (1, '테스트 공연 1', '부제', '설명', '2026-06-01', '2026-06-01', 100, 'GENERAL', '2026-05-12 10:00:00', '2026-06-01 13:00:00', NULL, 1, 90, 1, '2026-01-01 10:00:00', '시드');
INSERT INTO SHOWS (id, title, sub_title, info, start_date, end_date, view_count, display_sale_type, display_sale_starts_at, display_sale_ends_at, image, venue_id, running_minutes, performer_id, created_at, created_by) VALUES (2, '테스트 공연 2', '부제', '설명', '2026-07-01', '2026-07-01', 100, 'GENERAL', '2026-06-12 10:00:00', '2026-07-01 13:00:00', NULL, 2, 90, 1, '2026-01-01 10:00:00', '시드');

INSERT INTO SHOW_GENRES (id, show_id, genre_id, created_at, created_by) VALUES (1, 1, 1, '2026-01-01 10:00:00', '시드');
INSERT INTO SHOW_GENRES (id, show_id, genre_id, created_at, created_by) VALUES (2, 2, 1, '2026-01-01 10:00:00', '시드');

-- VENUE 1의 좌석 템플릿
INSERT INTO SEATS (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by) VALUES (1, 1, '나', 'A', '1', 1, 129, 101, '2026-01-01 10:00:00', '시드');
INSERT INTO SEATS (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by) VALUES (2, 1, '가', 'B', '2', 1, 136, 109, '2026-01-01 10:00:00', '시드');
INSERT INTO SEATS (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by) VALUES (3, 1, '라', 'C', '3', 1, 143, 117, '2026-01-01 10:00:00', '시드');

-- VENUE별 좌석 재생성. 실제 시드와 같이 VENUE 1 템플릿을 나머지 VENUE에 결정적으로 복제한다.
INSERT INTO SEATS (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by)
SELECT t.id + (v.id - 1) * 3, v.id, t.section, t.row_no, t.seat_no, t.floor, t.x, t.y, '2026-01-01 10:00:00', '시드'
FROM SEATS t
CROSS JOIN VENUES v
WHERE t.venue_id = 1 AND v.id <> 1;

INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES (1, 1, 1, '2026-06-01 14:00:00', '2026-06-01 15:30:00', '2026-05-12 10:00:00', '2026-06-01 13:00:00', 4, 600, '2026-01-01 10:00:00', '시드');
INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES (2, 1, 2, '2026-06-01 19:00:00', '2026-06-01 20:30:00', '2026-05-12 10:00:00', '2026-06-01 18:00:00', NULL, 600, '2026-01-01 10:00:00', '시드');
INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES (3, 2, 1, '2026-07-01 19:00:00', '2026-07-01 20:30:00', '2026-06-12 10:00:00', '2026-07-01 18:00:00', 4, 600, '2026-01-01 10:00:00', '시드');

INSERT INTO GRADES (code, name, created_at, created_by)
SELECT g.grade_code, g.grade_name, '2026-01-01 10:00:00', '시드'
FROM (
    SELECT 'VIP' grade_code, 'VIP석' grade_name FROM dual
    UNION ALL SELECT 'R', 'R석' FROM dual
    UNION ALL SELECT 'S', 'S석' FROM dual
    UNION ALL SELECT 'A', 'A석' FROM dual
) g
WHERE NOT EXISTS (SELECT 1 FROM GRADES existing WHERE existing.code = g.grade_code);

INSERT INTO PERFORMANCE_GRADES (performance_id, grade_id, price, sort_order, created_at, created_by)
SELECT p.id, gr.id,
    CASE gr.code
        WHEN 'VIP' THEN 170000
        WHEN 'R' THEN 140000
        WHEN 'S' THEN 110000
        ELSE 80000
    END,
    CASE gr.code
        WHEN 'VIP' THEN 1
        WHEN 'R' THEN 2
        WHEN 'S' THEN 3
        ELSE 4
    END,
    '2026-01-01 10:00:00', '시드'
FROM PERFORMANCES p
CROSS JOIN GRADES gr
WHERE gr.code IN ('VIP', 'R', 'S', 'A')
    AND NOT EXISTS (
        SELECT 1 FROM PERFORMANCE_GRADES existing
        WHERE existing.performance_id = p.id AND existing.grade_id = gr.id
    );

INSERT INTO PERFORMANCE_SEATS (performance_id, seat_id, state, performance_grade_id, unit_price, version, created_at, created_by)
SELECT p.id, st.id, 'AVAILABLE',
    (SELECT pg.id FROM PERFORMANCE_GRADES pg JOIN GRADES gg ON gg.id = pg.grade_id
        WHERE pg.performance_id = p.id AND gg.code = CASE WHEN st.section = '나' THEN 'VIP' WHEN st.section IN ('가', '다') THEN 'R' WHEN st.section IN ('라', '바') THEN 'S' ELSE 'A' END),
    (SELECT pg.price FROM PERFORMANCE_GRADES pg JOIN GRADES gg ON gg.id = pg.grade_id
        WHERE pg.performance_id = p.id AND gg.code = CASE WHEN st.section = '나' THEN 'VIP' WHEN st.section IN ('가', '다') THEN 'R' WHEN st.section IN ('라', '바') THEN 'S' ELSE 'A' END),
    0,
    '2026-01-01 10:00:00', '시드'
FROM PERFORMANCES p
JOIN SHOWS sh ON sh.id = p.show_id
JOIN SEATS st ON st.venue_id = sh.venue_id;
