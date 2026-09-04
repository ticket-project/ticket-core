package com.ticket.bootstrap.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ticket-domain-module-redesign Phase 2 Task 5(ADR 0005): catalog module의 {@code V5}(SHOW_GRADES ->
 * PERFORMANCE_GRADES 복제)와 {@code V6}(PERFORMANCE_SEATS.performance_grade_id/unit_price backfill)
 * migration이 실제로 만드는 결과를 검증한다.
 *
 * <p>검증 대상은 세 가지다.
 * <ul>
 *   <li>backfill 전후 row 수, 미매핑 수({@code performance_grade_id IS NULL}), 가격 불일치 수
 *   ({@code unit_price <> PERFORMANCE_GRADES.price}) -- {@link ShowGradePerformanceSeatPriceMismatchQueryTest}
 *   (Phase 1)가 고정한 탐지 방식을 새 schema(PerformanceGrade 기준)로 확장한다.</li>
 *   <li>PerformanceSeat가 다른 Performance의 PerformanceGrade에 잘못 연결되지 않는지.</li>
 *   <li>이 migration의 알려진 한계(V5/V6 SQL 파일 주석 참고) -- seed가 migration 실행 **이후**에 넣는
 *   SHOW_GRADES/SHOW_SEATS/PERFORMANCE_SEATS 행에 대해, 같은 SQL을 재실행(seed 재실행에 대응하는
 *   backfill 재실행)하면 새 행도 똑같이 매핑되고 기존 행은 그대로인지(멱등성).</li>
 * </ul>
 */
class ShowGradePerformanceGradeBackfillMigrationTest {

    private static final String URL =
            "jdbc:h2:mem:show-grade-performance-grade-backfill;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void v5_v6이_show_grade_show_seat를_performance_grade_performance_seat로_정확히_옮기고_재실행해도_멱등하다() throws Exception {
        createLegacyBaselineSchema();
        insertShow1Fixture();
        // Phase 1 스타일 stale price 재현: perf4/seat1은 VIP(170000)이지만 PerformanceSeat.price가
        // 150000으로 뒤처져 있다 -- ShowGrade 가격만 바뀌고 PerformanceSeat.price는 갱신되지 않은
        // 상황(ShowGradePerformanceSeatPriceMismatchQueryTest와 같은 시나리오)이다.
        insertPerformance(104L, 1L);
        insertPerformanceSeat(104L, 201L, new BigDecimal("150000.00"));

        // __root와 catalog의 migration만 적용한다(V1, V3, V4, V5, V6).
        ModulithFlywayTestSupport.migrate(URL, List.of("catalog"));

        try (Connection connection = ModulithFlywayTestSupport.connect(URL)) {
            // GRADES는 grade_code당 하나만 만든다.
            assertThat(gradeCodes(connection)).containsExactlyInAnyOrder("VIP", "R");

            // PERFORMANCE_GRADES: Performance 3개(101, 102, 104) x Grade 2개(VIP, R) = 6행.
            assertThat(countRows(connection, "PERFORMANCE_GRADES")).isEqualTo(6);
            assertThat(performanceGradePrice(connection, 101L, "VIP")).isEqualByComparingTo("170000.00");
            assertThat(performanceGradePrice(connection, 101L, "R")).isEqualByComparingTo("140000.00");
            assertThat(performanceGradePrice(connection, 104L, "VIP")).isEqualByComparingTo("170000.00");

            // backfill 전후 PERFORMANCE_SEATS row 수는 바뀌지 않는다 -- 컬럼만 채운다.
            assertThat(countRows(connection, "PERFORMANCE_SEATS")).isEqualTo(5);

            // 미매핑 수: 0.
            assertThat(unmappedPerformanceSeatCount(connection)).isZero();

            // 다른 Performance의 PerformanceGrade에 잘못 연결된 수: 0.
            assertThat(crossPerformanceLinkCount(connection)).isZero();

            // 가격 불일치 수: perf4/seat1 한 건만 (150000 stale vs PerformanceGrade 170000).
            assertThat(priceMismatchPerformanceSeatIds(connection, 101L, 201L)).isEmpty();
            assertThat(priceMismatchPerformanceSeatIds(connection, 104L, 201L)).containsExactly(
                    performanceSeatId(connection, 104L, 201L));
            assertThat(mismatchCount(connection)).isEqualTo(1);

            // seed 재실행 시나리오: migration이 이미 실행된 뒤에 seed가 새 Show/Performance/Seat
            // 데이터를 추가로 넣는다(V5/V6 주석의 알려진 한계를 그대로 재현).
            insertShow2Fixture(connection);

            // 같은 backfill SQL(V5, V6)을 다시 실행한다 -- 실제 배포에서는 반복 실행 불가능한 versioned
            // Flyway migration이지만, "seed 이후에도 이 SQL이 멱등하게 같은 결과를 내는지"는 SQL
            // 파일을 직접 재실행해 검증한다.
            executeSqlResource(connection, "db/migration-vendor/h2/catalog/V5__backfill_performance_grades_from_show_grades.sql");
            executeSqlResource(connection, "db/migration-vendor/h2/catalog/V6__backfill_performance_seat_grade_and_price.sql");

            // 새 Show(203)의 grade_code S는 새 GRADES row로 추가된다.
            assertThat(gradeCodes(connection)).containsExactlyInAnyOrder("VIP", "R", "S");
            // 새 Performance(105) x 기존 Grade 포함 전체가 아니라, 새 Show(2)의 Grade(S) 하나만 추가
            // 된다 -- PERFORMANCE_GRADES는 6 + 1 = 7행.
            assertThat(countRows(connection, "PERFORMANCE_GRADES")).isEqualTo(7);
            assertThat(performanceGradePrice(connection, 105L, "S")).isEqualByComparingTo("110000.00");

            // 새 PerformanceSeat(105, 205)도 매핑됐고, 기존 5행은 재실행으로 값이 바뀌지 않았다
            // (WHERE performance_grade_id IS NULL 가드가 멱등성을 보장한다).
            assertThat(countRows(connection, "PERFORMANCE_SEATS")).isEqualTo(6);
            assertThat(unmappedPerformanceSeatCount(connection)).isZero();
            assertThat(crossPerformanceLinkCount(connection)).isZero();
            assertThat(mismatchCount(connection)).isEqualTo(1); // perf4/seat1 stale row는 그대로 남는다.
            assertThat(performanceGradePrice(connection, 101L, "VIP")).isEqualByComparingTo("170000.00");
        }
    }

    // ---- fixture 구성 ----

    /**
     * SHOWS/SHOW_GRADES/SHOW_SEATS/PERFORMANCE_SEATS는 어떤 Flyway migration도 만들지 않는
     * pre-Flyway baseline이다(V5/V6 SQL 주석, docs/operations.md 참고). {@link
     * CatalogModuleSlicingSchemaTest}와 같은 최소 baseline을 재현한다.
     */
    private void createLegacyBaselineSchema() throws Exception {
        try (Connection connection = ModulithFlywayTestSupport.connect(URL);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SHOWS (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE PERFORMANCES (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL)");
            statement.execute(
                    "CREATE TABLE SHOW_GRADES (" +
                    "  id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL, grade_code VARCHAR(20) NOT NULL, " +
                    "  grade_name VARCHAR(255) NOT NULL, price DECIMAL(19,2) NOT NULL, sort_order INT NOT NULL" +
                    ")");
            statement.execute(
                    "CREATE TABLE SHOW_SEATS (" +
                    "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "  show_id BIGINT NOT NULL, seat_id BIGINT NOT NULL, show_grade_id BIGINT NOT NULL" +
                    ")");
            statement.execute(
                    "CREATE TABLE PERFORMANCE_SEATS (" +
                    "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "  performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL, price DECIMAL(19,2)" +
                    ")");
            statement.execute("CREATE TABLE ORDER_SEATS (order_id BIGINT NOT NULL)");
            statement.execute("""
                    CREATE TABLE VENUES (
                      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      name VARCHAR(255), address VARCHAR(255), region VARCHAR(50),
                      address_detail VARCHAR(255), zip_code VARCHAR(20),
                      latitude DECIMAL(10,8), longitude DECIMAL(11,8),
                      phone VARCHAR(255), image_url VARCHAR(255),
                      view_box_width INT NOT NULL, view_box_height INT NOT NULL,
                      seat_diameter DOUBLE NOT NULL, gap_x DOUBLE NOT NULL, gap_y DOUBLE NOT NULL,
                      created_at TIMESTAMP NOT NULL, created_by VARCHAR(255) NOT NULL,
                      updated_at TIMESTAMP, updated_by VARCHAR(255)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE SEATS (
                      id BIGINT PRIMARY KEY,
                      section VARCHAR(255) NOT NULL, row_no VARCHAR(255) NOT NULL,
                      seat_no VARCHAR(255) NOT NULL, floor INT NOT NULL,
                      x DOUBLE NOT NULL, y DOUBLE NOT NULL,
                      created_at TIMESTAMP NOT NULL, created_by VARCHAR(255) NOT NULL,
                      updated_at TIMESTAMP, updated_by VARCHAR(255)
                    )
                    """);
            insertVenue(connection, 1L);
        }
    }

    /**
     * Show 1: Performance 101/102, Seat 201(VIP)/202(R). seed와 같은 방식으로 PerformanceSeat.price를
     * ShowGrade.price에서 그대로 복사한다({@code ShowGradePerformanceSeatPriceMismatchQueryTest}의
     * "seed와_같은_방식으로_생성된_가격은_불일치가_없다"와 같은 전제).
     */
    private void insertShow1Fixture() throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(URL)) {
            insertShow(connection, 1L);
            insertPerformance(101L, 1L);
            insertPerformance(102L, 1L);
            insertSeat(connection, 201L);
            insertSeat(connection, 202L);
            insertShowGrade(connection, 301L, 1L, "VIP", "VIP석", new BigDecimal("170000.00"), 1);
            insertShowGrade(connection, 302L, 1L, "R", "R석", new BigDecimal("140000.00"), 2);
            insertShowSeat(connection, 1L, 201L, 301L);
            insertShowSeat(connection, 1L, 202L, 302L);
            insertPerformanceSeat(101L, 201L, new BigDecimal("170000.00"));
            insertPerformanceSeat(101L, 202L, new BigDecimal("140000.00"));
            insertPerformanceSeat(102L, 201L, new BigDecimal("170000.00"));
            insertPerformanceSeat(102L, 202L, new BigDecimal("140000.00"));
        }
    }

    /**
     * migration이 이미 실행된 뒤 seed가 새로 넣는 데이터를 재현한다(Show 2, Performance 105, Seat 205,
     * grade_code S).
     */
    private void insertShow2Fixture(final Connection connection) throws SQLException {
        insertShow(connection, 2L);
        insertPerformance(105L, 2L);
        insertSeatAfterVenueMigration(connection, 205L);
        insertShowGrade(connection, 303L, 2L, "S", "S석", new BigDecimal("110000.00"), 1);
        insertShowSeat(connection, 2L, 205L, 303L);
        insertPerformanceSeat(105L, 205L, new BigDecimal("110000.00"));
    }

    private void insertVenue(final Connection connection, final long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO VENUES (id, name, view_box_width, view_box_height, seat_diameter, gap_x, gap_y, "
                        + "created_at, created_by) VALUES (?, 'venue', 500, 356, 4.8, 2.5, 2.5, CURRENT_TIMESTAMP, 'test')")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private void insertShow(final Connection connection, final long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO SHOWS (id) VALUES (?)")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private void insertPerformance(final long id, final long showId) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(URL);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO PERFORMANCES (id, show_id) VALUES (?, ?)")) {
            statement.setLong(1, id);
            statement.setLong(2, showId);
            statement.executeUpdate();
        }
    }

    /** V3(Seat-Venue) 실행 전 baseline 형태 -- 이 시점 SEATS에는 아직 venue_id 컬럼이 없다. */
    private void insertSeat(final Connection connection, final long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO SEATS (id, section, row_no, seat_no, floor, x, y, created_at, created_by) "
                        + "VALUES (?, '가', 'A', ?, 1, 0, 0, CURRENT_TIMESTAMP, 'test')")) {
            statement.setLong(1, id);
            // 같은 Venue 안 좌석 주소 unique 제약(UK_SEATS_VENUE_SEAT_ADDRESS)을 피하려고 seat_no를
            // id로 다르게 준다 -- 이 테스트는 좌석 주소 자체가 아니라 grade/price backfill을 본다.
            statement.setString(2, String.valueOf(id));
            statement.executeUpdate();
        }
    }

    /**
     * V3(Seat-Venue) 실행 이후 seed 재실행 시나리오에서 넣는 seat -- 이 시점 SEATS.venue_id는 이미
     * NOT NULL이므로 값을 채워야 한다.
     */
    private void insertSeatAfterVenueMigration(final Connection connection, final long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO SEATS (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by) "
                        + "VALUES (?, 1, '가', 'A', ?, 1, 0, 0, CURRENT_TIMESTAMP, 'test')")) {
            statement.setLong(1, id);
            statement.setString(2, String.valueOf(id));
            statement.executeUpdate();
        }
    }

    private void insertShowGrade(
            final Connection connection,
            final long id,
            final long showId,
            final String code,
            final String name,
            final BigDecimal price,
            final int sortOrder
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO SHOW_GRADES (id, show_id, grade_code, grade_name, price, sort_order) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setLong(2, showId);
            statement.setString(3, code);
            statement.setString(4, name);
            statement.setBigDecimal(5, price);
            statement.setInt(6, sortOrder);
            statement.executeUpdate();
        }
    }

    private void insertShowSeat(final Connection connection, final long showId, final long seatId, final long showGradeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO SHOW_SEATS (show_id, seat_id, show_grade_id) VALUES (?, ?, ?)")) {
            statement.setLong(1, showId);
            statement.setLong(2, seatId);
            statement.setLong(3, showGradeId);
            statement.executeUpdate();
        }
    }

    private void insertPerformanceSeat(final long performanceId, final long seatId, final BigDecimal price) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(URL);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO PERFORMANCE_SEATS (performance_id, seat_id, price) VALUES (?, ?, ?)")) {
            statement.setLong(1, performanceId);
            statement.setLong(2, seatId);
            statement.setBigDecimal(3, price);
            statement.executeUpdate();
        }
    }

    // ---- 검증 질의 ----

    private List<String> gradeCodes(final Connection connection) throws SQLException {
        final List<String> codes = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT code FROM GRADES")) {
            while (rows.next()) {
                codes.add(rows.getString("code"));
            }
        }
        return codes;
    }

    private int countRows(final Connection connection, final String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private BigDecimal performanceGradePrice(final Connection connection, final long performanceId, final String gradeCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg.price FROM PERFORMANCE_GRADES pg JOIN GRADES g ON g.id = pg.grade_id "
                        + "WHERE pg.performance_id = ? AND g.code = ?")) {
            statement.setLong(1, performanceId);
            statement.setString(2, gradeCode);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getBigDecimal("price");
            }
        }
    }

    private long performanceSeatId(final Connection connection, final long performanceId, final long seatId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM PERFORMANCE_SEATS WHERE performance_id = ? AND seat_id = ?")) {
            statement.setLong(1, performanceId);
            statement.setLong(2, seatId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong("id");
            }
        }
    }

    private int unmappedPerformanceSeatCount(final Connection connection) throws SQLException {
        return countRows(connection, "PERFORMANCE_SEATS", "performance_grade_id IS NULL");
    }

    private int crossPerformanceLinkCount(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM PERFORMANCE_SEATS ps "
                             + "JOIN PERFORMANCE_GRADES pg ON pg.id = ps.performance_grade_id "
                             + "WHERE pg.performance_id <> ps.performance_id")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private int mismatchCount(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM PERFORMANCE_SEATS ps "
                             + "JOIN PERFORMANCE_GRADES pg ON pg.id = ps.performance_grade_id "
                             + "WHERE ps.unit_price <> pg.price")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    /** 같은 Performance/Seat의 PerformanceSeat가 자신의 PerformanceGrade와 가격이 불일치하는지 찾는다. */
    private List<Long> priceMismatchPerformanceSeatIds(final Connection connection, final long performanceId, final long seatId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ps.id FROM PERFORMANCE_SEATS ps "
                        + "JOIN PERFORMANCE_GRADES pg ON pg.id = ps.performance_grade_id "
                        + "WHERE ps.performance_id = ? AND ps.seat_id = ? AND ps.unit_price <> pg.price")) {
            statement.setLong(1, performanceId);
            statement.setLong(2, seatId);
            final List<Long> ids = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ids.add(rows.getLong("id"));
                }
            }
            return ids;
        }
    }

    private int countRows(final Connection connection, final String tableName, final String where) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + tableName + " WHERE " + where)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    /**
     * V5/V6 migration 파일을 직접 재실행한다. Flyway는 같은 버전을 두 번 적용하지 않으므로, "seed가
     * migration 실행 이후에 넣은 데이터에도 이 SQL을 다시 돌리면 같은 결과가 나오는지"는 파일
     * 내용을 그대로 읽어 재실행해 확인한다({@code SeedDataLoader.parseStatements}와 같은 방식으로
     * 주석을 건너뛰고 세미콜론으로 문장을 나눈다).
     *
     * <p>V6의 {@code ALTER TABLE ... ADD} 두 문장은 (Oracle 호환을 위해 {@code IF NOT EXISTS}를 쓰지
     * 않으므로) 컬럼이 이미 있는 상태에서 다시 실행하면 실패한다 -- 실제 배포에서는 Flyway가 같은
     * version을 두 번 적용하지 않으니 문제가 되지 않는다. 이 재실행은 순수 테스트 목적의 "같은 SQL을
     * 새 데이터에 다시 돌려도 UPDATE가 멱등한지" 검증이므로, 이미 존재하는 컬럼을 다시 추가하려는
     * 실패만 무시하고 나머지는 그대로 전파한다.
     */
    private void executeSqlResource(final Connection connection, final String resourcePath) throws IOException, SQLException {
        final List<String> statements = readStatements(resourcePath);
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                try {
                    statement.execute(sql);
                } catch (SQLException e) {
                    final String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                    final boolean columnAlreadyExists = sql.contains("ADD")
                            && (message.contains("already exists") || message.contains("duplicate column"));
                    if (!columnAlreadyExists) {
                        throw e;
                    }
                }
            }
        }
    }

    private List<String> readStatements(final String resourcePath) throws IOException {
        final List<String> statements = new ArrayList<>();
        final StringBuilder current = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(resourcePath).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    statements.add(current.toString().trim().replaceAll(";\\s*$", ""));
                    current.setLength(0);
                }
            }
        }
        if (!current.isEmpty()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}
