package com.ticket.bootstrap.migration;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ticket-domain-module-redesign Phase 1 / Task 1: 현재 {@code SHOW_GRADES}(Show 단위 등급 가격)와
 * {@code PERFORMANCE_SEATS}(회차 좌석 판매가) 사이의 가격 불일치를 탐지하는 query를 고정한다.
 *
 * <p><b>왜 이 query가 필요한가.</b> 지금 {@code PerformanceSeat}는 {@code ShowGrade}를 참조하지 않고
 * {@code price} 컬럼만 독립적으로 갖는다(seed가 삽입 시점에 값을 복사할 뿐이다,
 * {@code src/main/resources/seed/kopis-curated.sql}의 {@code PERFORMANCE_SEATS} CROSS JOIN INSERT
 * 참고). 두 값의 갱신 규칙은 코드로 보장되지 않으므로 시간이 지나 등급 가격만 바뀌면 조용히
 * 어긋날 수 있다 — Phase 2/3의 {@code PerformanceGrade}/{@code PerformanceSeat.unitPrice} backfill이
 * 이 gap을 없애야 한다({@code docs/superpowers/specs/.../가격의-원본과-스냅샷} 참고).
 *
 * <p>대응 관계는 {@code PERFORMANCE_SEATS.performance_id -> PERFORMANCES.show_id}와
 * {@code PERFORMANCE_SEATS.seat_id}를 이용해 같은 Show의 {@code SHOW_SEATS}(같은 seat_id)를 찾고, 그
 * {@code SHOW_SEATS.show_grade_id}가 가리키는 {@code SHOW_GRADES.price}와 비교한다. 이 join 경로는
 * Phase 2 Task 5의 backfill migration이 그대로 재사용할 수 있다.
 */
class ShowGradePerformanceSeatPriceMismatchQueryTest {

    private static final String URL_PREFIX = "show-grade-performance-seat-price-mismatch-";

    /**
     * 두 테이블의 join에 필요한 최소 컬럼만 갖는 baseline schema. 실제 운영 schema의 전체 컬럼은
     * {@link CurrentSeatVenueShowGradeSchemaTest}가 별도로 고정한다 — 이 테스트는 가격 비교 query
     * 자체가 정확한지에 집중한다.
     */
    private static final String CREATE_SCHEMA = """
            CREATE TABLE SHOWS (id BIGINT PRIMARY KEY);
            CREATE TABLE PERFORMANCES (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL);
            CREATE TABLE SEATS (id BIGINT PRIMARY KEY);
            CREATE TABLE SHOW_GRADES (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL, grade_code VARCHAR(20) NOT NULL, price DECIMAL(19,2) NOT NULL);
            CREATE TABLE SHOW_SEATS (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL, seat_id BIGINT NOT NULL, show_grade_id BIGINT NOT NULL);
            CREATE TABLE PERFORMANCE_SEATS (id BIGINT PRIMARY KEY, performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL, price DECIMAL(19,2) NOT NULL);
            """;

    /**
     * 같은 Show 안에서 PerformanceSeat.price와 그 좌석이 속한 ShowGrade.price가 다른 row를 찾는다.
     * PerformanceSeat -> Performance -> Show -> ShowSeat(같은 seat_id) -> ShowGrade 순서로 연결한다.
     */
    private static final String MISMATCH_QUERY = """
            SELECT ps.id AS performance_seat_id, ps.price AS performance_seat_price, sg.price AS show_grade_price
            FROM PERFORMANCE_SEATS ps
            JOIN PERFORMANCES p ON p.id = ps.performance_id
            JOIN SHOW_SEATS ss ON ss.show_id = p.show_id AND ss.seat_id = ps.seat_id
            JOIN SHOW_GRADES sg ON sg.id = ss.show_grade_id
            WHERE ps.price <> sg.price
            """;

    @Test
    void seed와_같은_방식으로_생성된_가격은_불일치가_없다() throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url("consistent"))) {
            runSchema(connection);
            insertShowGradeAndSeat(connection, 1L, 1L, 10L, 100L, "R", "15000.00");
            // seed와 동일하게 PerformanceSeat.price를 ShowGrade.price에서 그대로 복사한다.
            insertPerformanceSeat(connection, 1L, 10L, 100L, "15000.00");

            assertThat(findMismatches(connection)).isEmpty();
        }
    }

    @Test
    void show_grade_가격만_바뀌면_불일치를_탐지한다() throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url("stale"))) {
            runSchema(connection);
            insertShowGradeAndSeat(connection, 1L, 1L, 10L, 100L, "R", "15000.00");
            // 운영자가 이후 ShowGrade 가격만 올리고 이미 만들어진 PerformanceSeat.price는
            // 그대로 남은 상황을 재현한다 — 코드로 갱신을 보장하지 않는 지금 구조의 실제 위험이다.
            try (Statement statement = connection.createStatement()) {
                statement.execute("UPDATE SHOW_GRADES SET price = 18000.00 WHERE id = 100");
            }
            insertPerformanceSeat(connection, 1L, 10L, 100L, "15000.00");

            final List<Long> mismatchedPerformanceSeatIds = findMismatches(connection);
            assertThat(mismatchedPerformanceSeatIds).containsExactly(100L);
        }
    }

    private void runSchema(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String ddl : CREATE_SCHEMA.split(";")) {
                if (!ddl.isBlank()) {
                    statement.execute(ddl.trim());
                }
            }
        }
    }

    private void insertShowGradeAndSeat(
            final Connection connection,
            final long showId,
            final long performanceId,
            final long seatId,
            final long showGradeId,
            final String gradeCode,
            final String price
    ) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO SHOWS VALUES (" + showId + ")");
            statement.execute("INSERT INTO PERFORMANCES VALUES (" + performanceId + ", " + showId + ")");
            statement.execute("INSERT INTO SEATS VALUES (" + seatId + ")");
            statement.execute(
                    "INSERT INTO SHOW_GRADES VALUES (" + showGradeId + ", " + showId + ", '" + gradeCode + "', " + price + ")");
            statement.execute(
                    "INSERT INTO SHOW_SEATS VALUES (1, " + showId + ", " + seatId + ", " + showGradeId + ")");
        }
    }

    private void insertPerformanceSeat(
            final Connection connection,
            final long performanceId,
            final long seatId,
            final long performanceSeatId,
            final String price
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO PERFORMANCE_SEATS VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, performanceSeatId);
            statement.setLong(2, performanceId);
            statement.setLong(3, seatId);
            statement.setBigDecimal(4, new java.math.BigDecimal(price));
            statement.executeUpdate();
        }
    }

    private List<Long> findMismatches(final Connection connection) throws SQLException {
        final List<Long> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(MISMATCH_QUERY)) {
            while (rows.next()) {
                ids.add(rows.getLong("performance_seat_id"));
            }
        }
        return ids;
    }

    private String url(final String name) {
        return "jdbc:h2:mem:" + URL_PREFIX + name + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }
}
