package com.ticket.seed;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 부하 측정 전에 쌓여 있어야 할 과거 주문 이력({@code ORDERS}·{@code ORDER_SEATS})을 만든다.
 *
 * <p>빈 주문 테이블로 재면 인덱스가 없는 조회도 빠르게 보인다. 2026-10-01 로컬 측정에서 같은 부하·같은 풀인데 주문이 3.6천 건일 때 p95 체류 80ms, 1.4만 건일 때 1,103ms였다.
 * 그래서 운영 규모를 가정한 양을 미리 넣는다.
 *
 * <ul>
 *   <li><b>공용 회차에만 넣는다.</b> 부하 픽스처 회차({@link LoadTestFixtureSeeder#ID_BASE} 이상)에 주문이 있으면 측정 사용자가 비즈니스 거절을 받는다.
 *   <li><b>PENDING을 만들지 않는다.</b> 만료 worker가 배경 주문을 처리하기 시작하면 측정과 무관한 쓰기가 섞인다. CONFIRMED·EXPIRED·CANCELED만 쓴다.
 *   <li><b>CONFIRMED는 RESERVED 좌석에만 붙인다.</b> 회차 좌석 상태와 주문이 어긋나지 않게 RESERVED 좌석 하나에 확정 주문 하나다. 나머지는 아무 좌석이나 만료·취소로 만든다.
 *   <li>회원은 활성 회원 전체에서 고르고, 회차마다 같은 수를 나눈다.
 * </ul>
 *
 * <p>ID는 {@code ORDER_ID_BASE + performanceId × ORDERS_PER_PERFORMANCE_LIMIT + n}으로 직접 정한다. 주문 좌석이 주문 ID를 알아야 하는데
 * identity를 쓰면 되돌려 받을 방법이 없기 때문이다. 이 대역은 정리 기준도 된다({@code created_by = 'LOAD_TEST_BACKGROUND'}와 같다).
 *
 * <p>회차 하나가 한 트랜잭션이다. 배경 주문이 이미 있는 회차는 건너뛰므로 중간에 끊겨도 다시 실행하면 이어서 넣는다.
 *
 * <p>결제({@code PAYMENTS})·티켓({@code TICKETS})·선점 이력({@code HOLD_HISTORY})은 만들지 않는다. 측정 경로가 읽지 않는 테이블이다.
 */
final class BackgroundOrderSeeder implements SeedTask {
    static final long ORDER_ID_BASE = 800_000_000_000L;
    static final long ORDERS_PER_PERFORMANCE_LIMIT = 100_000L;
    static final String CREATED_BY = "LOAD_TEST_BACKGROUND";
    private static final int PROGRESS_EVERY = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int orderCount;
    private final boolean oracle;

    BackgroundOrderSeeder(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final int orderCount,
            final boolean oracle) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.orderCount = orderCount;
        this.oracle = oracle;
    }

    @Override
    public String name() {
        return "배경 주문 이력 적재";
    }

    @Override
    public Outcome run() {
        if (orderCount <= 0) {
            return Outcome.skipped("요청 주문 수가 0이라 적재하지 않습니다.");
        }

        final List<PerformanceRow> performances = jdbcTemplate.query(
                """
                        SELECT p.id, p.start_time, s.title, v.name
                        FROM PERFORMANCES p
                        JOIN SHOWS s ON s.id = p.show_id
                        JOIN VENUES v ON v.id = s.venue_id
                        WHERE p.id < ?
                        ORDER BY p.id
                        """,
                (rs, rowNum) -> new PerformanceRow(rs.getLong(1), rs.getTimestamp(2), rs.getString(3), rs.getString(4)),
                LoadTestFixtureSeeder.ID_BASE);
        if (performances.isEmpty()) {
            throw new SeedFailure("배경 주문을 붙일 공용 회차가 없습니다. 공용 시드를 먼저 적재하세요.");
        }

        final long[] memberIds =
                jdbcTemplate
                        .queryForList("SELECT id FROM MEMBERS WHERE deleted_at IS NULL ORDER BY id", Long.class)
                        .stream()
                        .mapToLong(Long::longValue)
                        .toArray();
        if (memberIds.length == 0) {
            throw new SeedFailure("배경 주문에 쓸 활성 회원이 없습니다. -Dseed.load-test-members.count로 회원을 먼저 만드세요.");
        }

        final int base = orderCount / performances.size();
        final int remainder = orderCount % performances.size();
        if (base + 1 >= ORDERS_PER_PERFORMANCE_LIMIT) {
            throw new SeedFailure("회차당 배경 주문이 %d건을 넘습니다. 주문 수를 줄이세요.".formatted(ORDERS_PER_PERFORMANCE_LIMIT));
        }

        final Set<Long> seeded = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT performance_id FROM ORDERS WHERE id >= ?", Long.class, ORDER_ID_BASE));

        long created = 0;
        int skipped = 0;
        for (int index = 0; index < performances.size(); index++) {
            final PerformanceRow performance = performances.get(index);
            if (seeded.contains(performance.id())) {
                skipped++;
                continue;
            }
            final int quota = base + (index < remainder ? 1 : 0);
            created += transactionTemplate.execute(status -> seedPerformance(performance, quota, memberIds));
            if ((index + 1) % PROGRESS_EVERY == 0) {
                SeedConsole.info("  배경 주문: 회차 %d/%d, 새 주문 %,d건".formatted(index + 1, performances.size(), created));
            }
        }

        if (created > 0 && oracle) {
            // 대량 적재 직후 통계가 낡으면 옵티마이저가 테이블을 작게 보고 실행 계획을 잘못 고른다.
            for (final String table : List.of("ORDERS", "ORDER_SEATS", "MEMBERS")) {
                jdbcTemplate.execute("BEGIN DBMS_STATS.GATHER_TABLE_STATS(USER, '" + table + "'); END;");
            }
        }

        if (created == 0) {
            return Outcome.skipped("공용 회차 %d개에 배경 주문이 이미 있습니다. 중복 생성하지 않습니다.".formatted(skipped));
        }
        return Outcome.done("공용 회차 %d개에 주문 %,d건을 새로 만들었습니다(이미 있던 회차 %d개는 건너뜀, 회원 %,d명 사용)."
                .formatted(performances.size() - skipped, created, skipped, memberIds.length));
    }

    private long seedPerformance(final PerformanceRow performance, final int quota, final long[] memberIds) {
        final List<SeatRow> seats = jdbcTemplate.query(
                """
                        SELECT ps.id, ps.seat_id, ps.unit_price, ps.state, g.code, g.name,
                               st.floor, st.section, st.row_no, st.seat_no
                        FROM PERFORMANCE_SEATS ps
                        JOIN SEATS st ON st.id = ps.seat_id
                        JOIN PERFORMANCE_GRADES pg ON pg.id = ps.performance_grade_id
                        JOIN GRADES g ON g.id = pg.grade_id
                        WHERE ps.performance_id = ?
                        ORDER BY ps.id
                        """,
                (rs, rowNum) -> new SeatRow(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getBigDecimal(3),
                        "RESERVED".equals(rs.getString(4)),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getInt(7) + "F " + rs.getString(8) + "구역 " + rs.getString(9) + "열 " + rs.getString(10)
                                + "번"),
                performance.id());
        if (seats.size() < 2) {
            return 0;
        }

        final Random random = new Random(performance.id());
        final List<SeatRow> reserved = seats.stream().filter(SeatRow::reserved).toList();
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime startTime =
                performance.startTime() == null ? now : performance.startTime().toLocalDateTime();
        final List<Object[]> orders = new ArrayList<>(quota);
        final List<Object[]> orderSeats = new ArrayList<>(quota * 2);
        int reservedCursor = 0;

        for (int n = 0; n < quota; n++) {
            final long orderId = ORDER_ID_BASE + performance.id() * ORDERS_PER_PERFORMANCE_LIMIT + n;
            final int seatCount = random.nextInt(10) < 6 ? 1 : 2;
            final List<SeatRow> picked = new ArrayList<>(seatCount);
            final String status;
            if (reservedCursor < reserved.size()) {
                status = "CONFIRMED";
                while (picked.size() < seatCount && reservedCursor < reserved.size()) {
                    picked.add(reserved.get(reservedCursor++));
                }
            } else {
                status = random.nextInt(100) < 65 ? "EXPIRED" : "CANCELED";
                final int first = random.nextInt(seats.size());
                picked.add(seats.get(first));
                if (seatCount == 2) {
                    picked.add(seats.get((first + 1 + random.nextInt(seats.size() - 1)) % seats.size()));
                }
            }

            // 주문은 공연 전에 생긴다. 앞으로 열릴 회차라도 지금보다 미래 시각은 쓰지 않는다.
            LocalDateTime createdAt =
                    startTime.minusDays(1 + random.nextInt(60)).minusSeconds(random.nextInt(86_400));
            if (createdAt.isAfter(now)) {
                createdAt = now.minusSeconds(1 + random.nextInt(86_400));
            }
            final LocalDateTime expiresAt = createdAt.plusMinutes(10);
            final LocalDateTime finishedAt =
                    "EXPIRED".equals(status) ? expiresAt : createdAt.plusSeconds(30 + random.nextInt(270));

            BigDecimal total = BigDecimal.ZERO;
            for (final SeatRow seat : picked) {
                total = total.add(seat.unitPrice());
                orderSeats.add(new Object[] {
                    orderId,
                    seat.id(),
                    seat.seatId(),
                    seat.unitPrice(),
                    seat.gradeCode(),
                    seat.gradeName(),
                    seat.label(),
                    Timestamp.valueOf(createdAt),
                    CREATED_BY
                });
            }
            orders.add(new Object[] {
                orderId,
                memberIds[random.nextInt(memberIds.length)],
                performance.id(),
                "ORDER-BG-" + orderId,
                "HOLD-BG-" + orderId,
                status,
                total,
                Timestamp.valueOf(expiresAt),
                "CONFIRMED".equals(status) ? Timestamp.valueOf(finishedAt) : null,
                "EXPIRED".equals(status) ? Timestamp.valueOf(finishedAt) : null,
                "CANCELED".equals(status) ? Timestamp.valueOf(finishedAt) : null,
                performance.showTitle(),
                Timestamp.valueOf(startTime),
                performance.venueName(),
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(finishedAt),
                CREATED_BY
            });
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO ORDERS (
                  id, member_id, performance_id, order_key, hold_key, status, total_amount, expires_at,
                  confirmed_at, expired_at, canceled_at,
                  show_title_snapshot, performance_start_at_snapshot, venue_name_snapshot,
                  created_at, updated_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orders);
        jdbcTemplate.batchUpdate("""
                INSERT INTO ORDER_SEATS (
                  order_id, performance_seat_id, seat_id, price,
                  grade_code_snapshot, grade_name_snapshot, seat_label_snapshot,
                  created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orderSeats);
        return orders.size();
    }

    private record PerformanceRow(long id, Timestamp startTime, String showTitle, String venueName) {}

    private record SeatRow(
            long id,
            long seatId,
            BigDecimal unitPrice,
            boolean reserved,
            String gradeCode,
            String gradeName,
            String label) {}
}
