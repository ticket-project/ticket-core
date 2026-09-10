package com.ticket.seed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 로컬 부하 테스트 전용 회차·좌석 데이터를 만든다.
 *
 * <p>운영 Oracle의 {@code scripts/core-capacity/create-core-capacity-data.sql}과 같은 고정 ID 대역을
 * 쓴다. 그래야 Gatling Console이 고정해서 넘기는 좌석 시작 ID와 회차 배정표를 대상만 바꿔 그대로
 * 쓸 수 있다.
 *
 * <p>공용 시드({@link CuratedSeedLoader})가 먼저 실행돼 GRADES에 VIP/R/S/A code를 만들어 두면 이
 * 시더는 그 code를 재사용한다 — 같은 code로 GRADES row를 중복 생성하지 않는다.
 *
 * <p>트랜잭션은 {@link #run()} 전체를 감싼다. 중간에 실패하면 전부 되돌려야 다음 실행에서
 * {@code alreadySeeded()}가 반쯤 적재된 데이터를 보고 건너뛰는 일이 없다.
 */
final class LoadTestFixtureSeeder implements SeedTask {

    static final long ID_BASE = 910000000L;
    static final int SEAT_COUNT = 2000;

    private static final long VENUE_ID = ID_BASE + 1;
    private static final long SHOW_ID = ID_BASE + 1;
    private static final String CREATED_BY = "LOAD_TEST_FIXTURE";
    private static final int SEATS_PER_SECTION = 200;
    private static final int SEATS_PER_ROW = 10;
    private static final int SEATS_PER_VIEW_ROW = 50;
    private static final int BATCH_SIZE = 1000;

    /**
     * 회차·좌석 등급 배정에 쓰는 공통 정의다. code, name, price, sortOrder 순서다. ADR 0005 이후
     * 등급 가격은 회차(PerformanceGrade) 단위로만 존재하므로 show 단위 가격표(과거 ShowGrade)는
     * 만들지 않는다.
     */
    private static final String[][] GRADE_DEFS = {
            {"VIP", "VIP석", "150000", "1"},
            {"R", "R석", "120000", "2"},
            {"S", "S석", "90000", "3"},
            {"A", "A석", "60000", "4"}
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int performanceCount;

    LoadTestFixtureSeeder(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final int performanceCount
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.performanceCount = performanceCount;
    }

    @Override
    public String name() {
        return "부하 테스트 전용 회차·좌석 적재";
    }

    @Override
    public Outcome run() {
        if (performanceCount <= 0) {
            return Outcome.skipped("performance-count=%d 이므로 적재하지 않습니다.".formatted(performanceCount));
        }
        if (alreadySeeded()) {
            return Outcome.skipped(
                    "부하 테스트 전용 데이터가 이미 있습니다(showId=%d). 중복 적재하지 않습니다.".formatted(SHOW_ID));
        }

        transactionTemplate.executeWithoutResult(status -> seedLoadTestFixture());

        return Outcome.done("showId=%d, 회차 %d개, 물리 좌석 %d석, 회차좌석 %d행을 적재했습니다."
                .formatted(SHOW_ID, performanceCount, SEAT_COUNT, performanceCount * SEAT_COUNT));
    }

    private void seedLoadTestFixture() {
        final LocalDateTime now = LocalDateTime.now();
        seedVenue(now);
        seedShow(now);
        seedSeats(now);
        seedPerformances(now);
        seedSalesPolicies(now);
        final Map<String, Long> gradeIdsByCode = ensureGrades(now);
        seedPerformanceGrades(now, gradeIdsByCode);
        seedPerformanceSeats(now);
    }

    private boolean alreadySeeded() {
        final Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shows WHERE id = ?", Integer.class, SHOW_ID);
        return count != null && count > 0;
    }

    private void seedVenue(final LocalDateTime now) {
        jdbcTemplate.update("""
                        INSERT INTO venues (
                          id, name, address, region, address_detail, zip_code,
                          latitude, longitude, phone, image_url,
                          view_box_width, view_box_height, seat_diameter, gap_x, gap_y,
                          created_at, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                VENUE_ID, "[LOAD TEST] Core Capacity Venue", "부하테스트 전용", "SEOUL",
                "로컬 Core 부하 측정 전용 데이터", "00000",
                37.5, 127.0, "000-0000-0000", null,
                500, 356, 6.0, 9.0, 8.0,
                Timestamp.valueOf(now), CREATED_BY);
    }

    private void seedShow(final LocalDateTime now) {
        jdbcTemplate.update("""
                        INSERT INTO shows (
                          id, title, sub_title, info,
                          start_date, end_date, view_count, display_sale_type,
                          display_sale_starts_at, display_sale_ends_at, image,
                          venue_id, running_minutes, performer_id,
                          created_at, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                SHOW_ID,
                "[LOAD TEST] Core Admission Capacity 2000석",
                "로컬 부하테스트 전용 공연",
                "실제 사용자에게 노출하거나 판매하지 않는 부하 측정 전용 데이터",
                java.sql.Date.valueOf(now.toLocalDate().plusDays(60)),
                java.sql.Date.valueOf(now.toLocalDate().plusDays(90)),
                0L, "GENERAL",
                Timestamp.valueOf(now.minusDays(1)),
                Timestamp.valueOf(now.plusDays(30)),
                null, VENUE_ID, 120, null,
                Timestamp.valueOf(now), CREATED_BY);
    }

    private void seedSeats(final LocalDateTime now) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int index = 1; index <= SEAT_COUNT; index++) {
            final int section = (index - 1) / SEATS_PER_SECTION + 1;
            final int rowNo = ((index - 1) % SEATS_PER_SECTION) / SEATS_PER_ROW + 1;
            final int seatNo = (index - 1) % SEATS_PER_ROW + 1;
            batch.add(new Object[]{
                    ID_BASE + index,
                    VENUE_ID,
                    String.format("SEC-%02d", section),
                    String.format("ROW-%02d", rowNo),
                    String.format("%02d", seatNo),
                    section <= 6 ? 1 : 2,
                    20.0 + (index - 1) % SEATS_PER_VIEW_ROW * 9,
                    20.0 + (double) ((index - 1) / SEATS_PER_VIEW_ROW) * 8,
                    createdAt,
                    CREATED_BY
            });
            if (batch.size() == BATCH_SIZE || index == SEAT_COUNT) {
                jdbcTemplate.batchUpdate("""
                        INSERT INTO seats (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, batch);
                batch.clear();
            }
        }
    }

    /** 구역 1~2는 VIP, 3~4는 R, 5~7은 S, 나머지는 A로 나눈다. 운영 전용 데이터와 같은 비율이다. */
    private int gradeIndex(final int seatIndex) {
        final int section = (seatIndex - 1) / SEATS_PER_SECTION + 1;
        if (section <= 2) {
            return 0;
        }
        if (section <= 4) {
            return 1;
        }
        if (section <= 7) {
            return 2;
        }
        return 3;
    }

    private void seedPerformances(final LocalDateTime now) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<Object[]> batch = new ArrayList<>(performanceCount);
        for (int index = 1; index <= performanceCount; index++) {
            final LocalDateTime startTime = now.plusDays(60L + index);
            batch.add(new Object[]{
                    ID_BASE + index, SHOW_ID, index,
                    Timestamp.valueOf(startTime),
                    Timestamp.valueOf(startTime.plusMinutes(120)),
                    createdAt, CREATED_BY
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO performances (
                  id, show_id, performance_no, start_time, end_time,
                  created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, batch);
    }

    /**
     * ADR 0006 "Performance의 책임 혼재" A2: 예매 접수 기간·Hold 한도·대기열 정책은 Booking BC의
     * BOOKING_PERFORMANCE_SALES_POLICIES가 소유한다. FORCE_OFF는 이 부하 테스트 전용 회차가 Queue
     * 없이 Core를 직접 호출한다는 기존 의미를 그대로 보존한다.
     */
    private void seedSalesPolicies(final LocalDateTime now) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<Object[]> batch = new ArrayList<>(performanceCount);
        for (int index = 1; index <= performanceCount; index++) {
            batch.add(new Object[]{
                    ID_BASE + index,
                    Timestamp.valueOf(now.minusDays(1)),
                    Timestamp.valueOf(now.plusDays(30)),
                    2, 600L,
                    "FORCE_OFF", "LEVEL_1", null,
                    "로컬 Core 부하 측정 전용", "Queue 없이 Core를 직접 호출하는 전용 회차",
                    0L, createdAt, CREATED_BY
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO booking_performance_sales_policies (
                  performance_id, order_opens_at, order_closes_at, max_hold_seat_count, hold_duration_seconds,
                  queue_mode, queue_level, preopen_queue_starts_at,
                  waiting_room_message, queue_policy_reason,
                  version, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
    }

    /**
     * GRADES는 재사용 가능한 등급 코드다(ADR 0005). 공용 시드가 먼저 돌아 VIP/R/S/A code를 이미
     * 만들어 뒀을 수 있어, code당 하나만 있도록 존재하면 재사용하고 없으면 새로 만든다.
     */
    private Map<String, Long> ensureGrades(final LocalDateTime now) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final Map<String, Long> idsByCode = new LinkedHashMap<>();
        for (final String[] def : GRADE_DEFS) {
            final List<Long> existing = jdbcTemplate.queryForList(
                    "SELECT id FROM grades WHERE code = ?", Long.class, def[0]);
            if (existing.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO grades (code, name, created_at, created_by) VALUES (?, ?, ?, ?)",
                        def[0], def[1], createdAt, CREATED_BY);
                idsByCode.put(def[0], jdbcTemplate.queryForObject(
                        "SELECT id FROM grades WHERE code = ?", Long.class, def[0]));
            } else {
                idsByCode.put(def[0], existing.get(0));
            }
        }
        return idsByCode;
    }

    /**
     * PerformanceSeat.unitPrice의 원본은 PerformanceGrade.price다(ADR 0005) — 회차마다
     * PERFORMANCE_GRADES를 만든다.
     */
    private void seedPerformanceGrades(final LocalDateTime now, final Map<String, Long> gradeIdsByCode) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<Object[]> batch = new ArrayList<>(performanceCount * GRADE_DEFS.length);
        for (int performance = 1; performance <= performanceCount; performance++) {
            final long performanceId = ID_BASE + performance;
            for (final String[] grade : GRADE_DEFS) {
                batch.add(new Object[]{
                        performanceId, gradeIdsByCode.get(grade[0]), new BigDecimal(grade[2]),
                        Integer.parseInt(grade[3]), createdAt, CREATED_BY
                });
            }
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO performance_grades (
                  performance_id, grade_id, price, sort_order, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, batch);
    }

    private void seedPerformanceSeats(final LocalDateTime now) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<BigDecimal> prices = Arrays.stream(GRADE_DEFS)
                .map(def -> new BigDecimal(def[2]))
                .toList();
        for (int performance = 1; performance <= performanceCount; performance++) {
            final long performanceId = ID_BASE + performance;
            final List<Long> performanceGradeIds = jdbcTemplate.queryForList(
                    "SELECT id FROM performance_grades WHERE performance_id = ? ORDER BY sort_order",
                    Long.class, performanceId);
            final List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
            for (int index = 1; index <= SEAT_COUNT; index++) {
                final int gradeIdx = gradeIndex(index);
                batch.add(new Object[]{
                        performanceId, ID_BASE + index, "AVAILABLE",
                        performanceGradeIds.get(gradeIdx), prices.get(gradeIdx), 0L,
                        createdAt, CREATED_BY
                });
                if (batch.size() == BATCH_SIZE || index == SEAT_COUNT) {
                    jdbcTemplate.batchUpdate("""
                            INSERT INTO performance_seats (
                              performance_id, seat_id, state, performance_grade_id, unit_price, version,
                              created_at, created_by
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """, batch);
                    batch.clear();
                }
            }
        }
    }
}
