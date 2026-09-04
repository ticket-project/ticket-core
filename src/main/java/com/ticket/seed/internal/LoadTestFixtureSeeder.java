package com.ticket.seed.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 로컬 부하 테스트 전용 회차·좌석 데이터를 만든다.
 *
 * <p>운영 Oracle의 {@code scripts/core-capacity/create-core-capacity-data.sql}과 같은 고정 ID 대역을 쓴다.
 * 그래야 Gatling Console이 고정해서 넘기는 좌석 시작 ID와 회차 배정표를 대상만 바꿔 그대로 쓸 수 있다.
 *
 * <p>공용 시드인 {@link SeedDataLoader}와 {@code seed/kopis-curated.sql}은 로컬과 운영 양쪽에 쓰이므로
 * 건드리지 않는다. 이 컴포넌트는 {@code app.seed.load-test-fixture.enabled}가 참일 때만 동작하며
 * 기본값이 거짓이라 운영에는 어떤 경로로도 적재되지 않는다.
 */
@Slf4j
@Component
@Order(LoadTestFixtureSeeder.ORDER)
public class LoadTestFixtureSeeder implements ApplicationRunner {

    /**
     * 반드시 {@link SeedDataLoader} 다음에 실행해야 한다. 공용 시드의 SHOW_GRADES INSERT는
     * {@code FROM SHOWS s CROSS JOIN (VIP/R/S/A ...)}로 그 시점의 모든 SHOWS를 훑는다.
     * 이 시더가 먼저 돌면 전용 show에도 공용 등급이 덧붙어 grade_code가 중복되고,
     * SHOW_SEATS의 스칼라 서브쿼리가 2행을 반환해 기동이 실패한다. (SHOW_SEATS 자체는
     * {@code st.venue_id = s.venue_id}로 그 Show의 VENUE에 속한 SEATS만 훑으므로 이 시더가
     * 만드는 전용 VENUE/SEATS와는 섞이지 않는다.)
     */
    static final int ORDER = SeedDataLoader.ORDER + 100;

    static final long ID_BASE = 910000000L;
    static final int SEAT_COUNT = 2000;

    private static final long VENUE_ID = ID_BASE + 1;
    private static final long SHOW_ID = ID_BASE + 1;
    private static final String CREATED_BY = "LOAD_TEST_FIXTURE";
    private static final int SEATS_PER_SECTION = 200;
    private static final int SEATS_PER_ROW = 10;
    private static final int SEATS_PER_VIEW_ROW = 50;
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final int performanceCount;

    public LoadTestFixtureSeeder(
            final JdbcTemplate jdbcTemplate,
            @Value("${app.seed.load-test-fixture.enabled:false}") final boolean enabled,
            @Value("${app.seed.load-test-fixture.performance-count:8}") final int performanceCount
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.performanceCount = performanceCount;
    }

    /**
     * 트랜잭션 경계를 여기에 둔다. {@code seedLoadTestFixture()}에 붙이면 아래 자기 호출이
     * 프록시를 지나지 않아 트랜잭션이 걸리지 않는다. 중간에 실패하면 전부 되돌려야
     * 다음 기동에서 {@code alreadySeeded()}가 반쯤 적재된 데이터를 보고 건너뛰는 일이 없다.
     */
    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        seedLoadTestFixture();
    }

    public void seedLoadTestFixture() {
        if (!enabled) {
            log.info("부하 테스트 전용 데이터 시드를 건너뜁니다. app.seed.load-test-fixture.enabled=false");
            return;
        }
        if (performanceCount <= 0) {
            log.info("부하 테스트 전용 데이터 시드를 건너뜁니다. performance-count={}", performanceCount);
            return;
        }
        if (alreadySeeded()) {
            log.info("부하 테스트 전용 데이터가 이미 있습니다. 적재를 건너뜁니다. showId={}", SHOW_ID);
            return;
        }

        final LocalDateTime now = LocalDateTime.now();
        seedVenue(now);
        seedShow(now);
        seedSeats(now);
        final List<Long> gradeIds = seedShowGrades(now);
        seedShowSeats(now, gradeIds);
        seedPerformances(now);
        seedQueuePolicies(now);
        seedPerformanceSeats(now);

        log.info(
                "부하 테스트 전용 데이터 시드를 완료했습니다. showId={}, 회차={}, 좌석={}, 회차좌석={}",
                SHOW_ID, performanceCount, SEAT_COUNT, performanceCount * SEAT_COUNT
        );
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
                          start_date, end_date, view_count, sale_type,
                          sale_start_date, sale_end_date, image,
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

    private List<Long> seedShowGrades(final LocalDateTime now) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final String[][] grades = {
                {"VIP", "VIP석", "150000", "1"},
                {"R", "R석", "120000", "2"},
                {"S", "S석", "90000", "3"},
                {"A", "A석", "60000", "4"}
        };
        for (String[] grade : grades) {
            jdbcTemplate.update("""
                            INSERT INTO show_grades (
                              show_id, grade_code, grade_name, price, sort_order, created_at, created_by
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    SHOW_ID, grade[0], grade[1], new BigDecimal(grade[2]),
                    Integer.parseInt(grade[3]), createdAt, CREATED_BY);
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM show_grades WHERE show_id = ? ORDER BY sort_order",
                Long.class, SHOW_ID);
    }

    private void seedShowSeats(final LocalDateTime now, final List<Long> gradeIds) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int index = 1; index <= SEAT_COUNT; index++) {
            batch.add(new Object[]{
                    SHOW_ID, ID_BASE + index, gradeIds.get(gradeIndex(index)), createdAt, CREATED_BY
            });
            if (batch.size() == BATCH_SIZE || index == SEAT_COUNT) {
                jdbcTemplate.batchUpdate("""
                        INSERT INTO show_seats (show_id, seat_id, show_grade_id, created_at, created_by)
                        VALUES (?, ?, ?, ?, ?)
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
                    Timestamp.valueOf(now.minusDays(1)),
                    Timestamp.valueOf(now.plusDays(30)),
                    2, 600, createdAt, CREATED_BY
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO performances (
                  id, show_id, performance_no, start_time, end_time,
                  order_open_time, order_close_time, max_can_hold_count, hold_time,
                  created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
    }

    private void seedQueuePolicies(final LocalDateTime now) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<Object[]> batch = new ArrayList<>(performanceCount);
        for (int index = 1; index <= performanceCount; index++) {
            batch.add(new Object[]{
                    ID_BASE + index, "FORCE_OFF", "LEVEL_1", null,
                    "로컬 Core 부하 측정 전용", "Queue 없이 Core를 직접 호출하는 전용 회차",
                    createdAt, CREATED_BY
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO performance_queue_policies (
                  performance_id, queue_mode, queue_level, preopen_queue_start_at,
                  waiting_room_message, reason, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
    }

    private void seedPerformanceSeats(final LocalDateTime now) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<BigDecimal> prices = jdbcTemplate.queryForList(
                "SELECT price FROM show_grades WHERE show_id = ? ORDER BY sort_order",
                BigDecimal.class, SHOW_ID);
        for (int performance = 1; performance <= performanceCount; performance++) {
            final long performanceId = ID_BASE + performance;
            final List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
            for (int index = 1; index <= SEAT_COUNT; index++) {
                batch.add(new Object[]{
                        performanceId, ID_BASE + index, "AVAILABLE",
                        prices.get(gradeIndex(index)), createdAt, CREATED_BY
                });
                if (batch.size() == BATCH_SIZE || index == SEAT_COUNT) {
                    jdbcTemplate.batchUpdate("""
                            INSERT INTO performance_seats (
                              performance_id, seat_id, state, price, created_at, created_by
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """, batch);
                    batch.clear();
                }
            }
        }
    }
}
