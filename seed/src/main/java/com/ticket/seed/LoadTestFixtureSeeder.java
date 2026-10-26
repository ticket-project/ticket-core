package com.ticket.seed;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 부하 테스트 전용 회차·좌석 데이터를 만든다.
 *
 * <p>크기는 둘이다. 표준({@link #STANDARD}, 2,000석)은 {@code gatling-test} 저장소가 고정해 쓰는 ID 대역(Console {@code LoadTestService}의 좌석
 * 시작 ID, Gatling {@code LoadTestConfig}의 기본 공연 ID)과 같은 대역을 쓴다. 그래야 Gatling Console이 고정해서 넘기는 좌석 시작 ID와 회차 배정표를 대상만 바꿔
 * 그대로 쓸 수 있다. 대형({@link #LARGE}, 15,000석)은 큰 예매 사이트의 인기 공연(체조경기장급)을 가정한 수용량 측정 기준이다. Queue batch는 회차마다 적용되므로 입장률은 가장 큰
 * 회차에서도 버텨야 하고, 좌석 상태 응답은 좌석 수에 비례한다. 대형은 표준과 겹치지 않게 별도 ID 대역을 쓴다.
 *
 * <p>공연장·공연·물리 좌석은 처음 한 번만 만든다. 회차는 요청 수까지 <b>모자란 것만</b> 더한다. 회차는 한 번 쓰면 버리고 판매 기간도 적재 후 30일이라, 측정을 이어 가려면 나중에 회차를 늘릴 수
 * 있어야 한다.
 *
 * <p>공용 시드({@link CuratedSeedLoader})가 먼저 실행돼 GRADES에 VIP/R/S/A code를 만들어 두면 이 시더는 그 code를 재사용한다 — 같은 code로 GRADES row를
 * 중복 생성하지 않는다.
 *
 * <p>트랜잭션은 {@link #run()} 전체를 감싼다. 중간에 실패하면 전부 되돌려야 다음 실행에서 반쯤 적재된 회차를 "이미 있다"고 보고 건너뛰는 일이 없다.
 */
final class LoadTestFixtureSeeder implements SeedTask {
    /** 부하 픽스처 ID 대역의 시작이다. 이보다 작은 ID는 공용 시드다. 대형 대역도 이보다 크다. */
    static final long ID_BASE = 910000000L;

    static final int SEAT_COUNT = 2000;
    static final Size STANDARD = new Size(ID_BASE, SEAT_COUNT, 50, 500, 356);
    static final Size LARGE = new Size(920000000L, 15_000, 150, 1400, 850);
    private static final String CREATED_BY = "LOAD_TEST_FIXTURE";
    private static final int SEATS_PER_SECTION = 200;
    private static final int SEATS_PER_ROW = 10;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Size size;
    private final int performanceCount;

    LoadTestFixtureSeeder(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final Size size,
            final int performanceCount) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.size = size;
        this.performanceCount = performanceCount;
    }

    @Override
    public String name() {
        return "부하 테스트 전용 회차·좌석 적재(%,d석)".formatted(size.seatCount());
    }

    @Override
    public Outcome run() {
        if (performanceCount <= 0) {
            return Outcome.skipped("performance-count=%d 이므로 적재하지 않습니다.".formatted(performanceCount));
        }

        final Set<Long> existing = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT id FROM performances WHERE id > ? AND id <= ?",
                Long.class,
                size.idBase(),
                size.idBase() + performanceCount));
        final List<Integer> missing = IntStream.rangeClosed(1, performanceCount)
                .filter(index -> !existing.contains(size.idBase() + index))
                .boxed()
                .toList();
        if (missing.isEmpty()) {
            return Outcome.skipped(
                    "showId=%d에 회차 %d개가 이미 있습니다. 중복 적재하지 않습니다.".formatted(size.showId(), performanceCount));
        }

        final Boolean created = transactionTemplate.execute(status -> seedLoadTestFixture(missing));

        return Outcome.done("%sshowId=%d에 회차 %d개를 더했습니다(%d ~ %d, 회차좌석 %,d행)."
                .formatted(
                        Boolean.TRUE.equals(created) ? "공연장·공연·물리 좌석 %,d석을 만들고 ".formatted(size.seatCount()) : "",
                        size.showId(),
                        missing.size(),
                        size.idBase() + missing.getFirst(),
                        size.idBase() + missing.getLast(),
                        (long) missing.size() * size.seatCount()));
    }

    /** @return 공연장·공연·물리 좌석을 이번에 처음 만들었는지 */
    private boolean seedLoadTestFixture(final List<Integer> performanceIndexes) {
        final LocalDateTime now = LocalDateTime.now();
        final boolean create = !alreadySeeded();
        if (create) {
            seedVenue(now);
            seedShow(now);
            seedSeats(now);
        }
        seedPerformances(now, performanceIndexes);
        seedSalesPolicies(now, performanceIndexes);
        seedPerformanceGrades(now, performanceIndexes);
        seedPerformanceSeats(now, performanceIndexes);
        return create;
    }

    private boolean alreadySeeded() {
        final Integer count =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shows WHERE id = ?", Integer.class, size.showId());
        return count != null && count > 0;
    }

    private void seedVenue(final LocalDateTime now) {
        jdbcTemplate.update(
                """
                        INSERT INTO venues (
                          id, name, address, region, address_detail, zip_code,
                          latitude, longitude, phone, image_url,
                          view_box_width, view_box_height, seat_diameter, gap_x, gap_y,
                          created_at, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                size.venueId(),
                "[LOAD TEST] Core Capacity Venue %d석".formatted(size.seatCount()),
                "부하테스트 전용",
                "SEOUL",
                "Core 부하 측정 전용 데이터",
                "00000",
                37.5,
                127.0,
                "000-0000-0000",
                null,
                size.viewBoxWidth(),
                size.viewBoxHeight(),
                6.0,
                9.0,
                8.0,
                Timestamp.valueOf(now),
                CREATED_BY);
    }

    private void seedShow(final LocalDateTime now) {
        jdbcTemplate.update(
                """
                        INSERT INTO shows (
                          id, title, sub_title, info,
                          start_date, end_date, view_count, display_sale_type,
                          display_sale_starts_at, display_sale_ends_at, image,
                          venue_id, running_minutes, performer_id,
                          created_at, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                size.showId(),
                "[LOAD TEST] Core Admission Capacity %d석".formatted(size.seatCount()),
                "부하테스트 전용 공연",
                "실제 사용자에게 노출하거나 판매하지 않는 부하 측정 전용 데이터",
                java.sql.Date.valueOf(now.toLocalDate().plusDays(60)),
                java.sql.Date.valueOf(now.toLocalDate().plusDays(90)),
                0L,
                "GENERAL",
                Timestamp.valueOf(now.minusDays(1)),
                Timestamp.valueOf(now.plusDays(30)),
                null,
                size.venueId(),
                120,
                null,
                Timestamp.valueOf(now),
                CREATED_BY);
    }

    /**
     * 구역은 {@value #SEATS_PER_SECTION}석, 줄은 {@value #SEATS_PER_ROW}석이다. 앞 구역 60%가 1층이다(2,000석은 구역 1~6). 배치도 좌표는 한 줄에
     * {@code seatsPerViewRow}석씩 놓는다.
     */
    private void seedSeats(final LocalDateTime now) {
        jdbcTemplate.update(
                """
                        INSERT INTO seats (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by)
                        SELECT ? + i, ?,
                               'SEC-' || lpad(((i - 1) / %1$d + 1)::text, 2, '0'),
                               'ROW-' || lpad((((i - 1) %% %1$d) / %2$d + 1)::text, 2, '0'),
                               lpad(((i - 1) %% %2$d + 1)::text, 2, '0'),
                               CASE WHEN (i - 1) / %1$d * 10 < 6 * ? THEN 1 ELSE 2 END,
                               20.0 + (i - 1) %% ? * 9,
                               20.0 + (i - 1) / ? * 8,
                               ?, ?
                        FROM generate_series(1, ?) AS i
                        """.formatted(SEATS_PER_SECTION, SEATS_PER_ROW),
                size.idBase(),
                size.venueId(),
                size.sectionCount(),
                size.seatsPerViewRow(),
                size.seatsPerViewRow(),
                Timestamp.valueOf(now),
                CREATED_BY,
                size.seatCount());
    }

    private void seedPerformances(final LocalDateTime now, final List<Integer> performanceIndexes) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<Object[]> batch = new ArrayList<>(performanceIndexes.size());
        for (final int index : performanceIndexes) {
            final LocalDateTime startTime = now.plusDays(60L + index);
            batch.add(new Object[] {
                size.idBase() + index,
                size.showId(),
                index,
                Timestamp.valueOf(startTime),
                Timestamp.valueOf(startTime.plusMinutes(120)),
                createdAt,
                CREATED_BY
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
     * ADR 0006 "Performance의 책임 혼재" A2: 예매 기간·Hold 한도·대기열 정책은 Booking BC의 BOOKING_PERFORMANCE_SALES_POLICIES가 소유한다.
     * FORCE_OFF는 이 부하 테스트 전용 회차가 Queue 없이 Core를 직접 호출한다는 기존 의미를 그대로 보존한다. 판매 기간은 회차를 더한 시각부터 30일이다.
     */
    private void seedSalesPolicies(final LocalDateTime now, final List<Integer> performanceIndexes) {
        final Timestamp createdAt = Timestamp.valueOf(now);
        final List<Object[]> batch = new ArrayList<>(performanceIndexes.size());
        for (final int index : performanceIndexes) {
            batch.add(new Object[] {
                size.idBase() + index,
                Timestamp.valueOf(now.minusDays(1)),
                Timestamp.valueOf(now.plusDays(30)),
                2,
                600L,
                "FORCE_OFF",
                "LEVEL_1",
                null,
                "Core 부하 측정 전용",
                "Queue 없이 Core를 직접 호출하는 전용 회차",
                0L,
                createdAt,
                CREATED_BY
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
     * PerformanceSeat.unitPrice의 원본은 PerformanceGrade.price다(ADR 0005) — 회차마다 PERFORMANCE_GRADES를 만든다. GRADES의
     * VIP/R/S/A code는 앞서 실행된 공용 시드가 만들어 둔 것을 재사용한다.
     */
    private void seedPerformanceGrades(final LocalDateTime now, final List<Integer> performanceIndexes) {
        for (final int performance : performanceIndexes) {
            final int inserted =
                    jdbcTemplate.update("""
                            INSERT INTO performance_grades (performance_id, grade_id, price, sort_order, created_at, created_by)
                            SELECT ?, g.id, v.price, v.sort_order, ?, ?
                            FROM (VALUES ('VIP', 150000, 1), ('R', 120000, 2), ('S', 90000, 3), ('A', 60000, 4))
                                 AS v (code, price, sort_order)
                            JOIN grades g ON g.code = v.code
                            """, size.idBase() + performance, Timestamp.valueOf(now), CREATED_BY);
            if (inserted != 4) {
                throw new SeedFailure("GRADES에 VIP/R/S/A code가 모두 있어야 합니다. 공용 시드가 먼저 적재됐는지 확인하세요.");
            }
        }
    }

    /** 앞 구역부터 VIP 20%, R 20%, S 30%, A 30%로 나눈다. 2,000석(구역 10개)에서는 구역 1~2 VIP, 3~4 R, 5~7 S, 나머지 A로 운영 전용 데이터와 같다. */
    private void seedPerformanceSeats(final LocalDateTime now, final List<Integer> performanceIndexes) {
        for (final int performance : performanceIndexes) {
            jdbcTemplate.update(
                    """
                            INSERT INTO performance_seats (
                              performance_id, seat_id, state, performance_grade_id, unit_price, version,
                              created_at, created_by
                            )
                            SELECT pg.performance_id, ? + i, 'AVAILABLE', pg.id, pg.price, 0, ?, ?
                            FROM generate_series(1, ?) AS i
                            JOIN performance_grades pg
                              ON pg.performance_id = ?
                             AND pg.sort_order = CASE
                                   WHEN (i - 1) / %1$d * 10 < 2 * ? THEN 1
                                   WHEN (i - 1) / %1$d * 10 < 4 * ? THEN 2
                                   WHEN (i - 1) / %1$d * 10 < 7 * ? THEN 3
                                   ELSE 4
                                 END
                            """.formatted(SEATS_PER_SECTION),
                    size.idBase(),
                    Timestamp.valueOf(now),
                    CREATED_BY,
                    size.seatCount(),
                    size.idBase() + performance,
                    size.sectionCount(),
                    size.sectionCount(),
                    size.sectionCount());
        }
    }

    /**
     * 픽스처 크기 하나다. 공연장·공연·물리 좌석·회차가 모두 {@code idBase + n}을 ID로 쓴다. 모든 회차의 좌석은 {@code idBase + 1}부터 좌석 수만큼이다.
     *
     * @param seatsPerViewRow 배치도 한 줄에 놓는 좌석 수. view box 크기와 함께 정한다
     */
    record Size(long idBase, int seatCount, int seatsPerViewRow, int viewBoxWidth, int viewBoxHeight) {
        long venueId() {
            return idBase + 1;
        }

        long showId() {
            return idBase + 1;
        }

        int sectionCount() {
            return (seatCount + SEATS_PER_SECTION - 1) / SEATS_PER_SECTION;
        }
    }
}
