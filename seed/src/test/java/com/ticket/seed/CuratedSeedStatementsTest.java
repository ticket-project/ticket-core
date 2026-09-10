package com.ticket.seed;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code seed/sql/kopis-curated.sql}과 그 변환 결과가 지켜야 할 불변식을 고정한다. 예전
 * {@code com.ticket.seed.SeedDataLoaderTest}가 reflection으로 확인했던 것을 그대로 옮겼고,
 * 변환 계층이 DB를 모르는 순수 코드가 되면서 reflection이 필요 없어졌다.
 */
@SuppressWarnings("NonAsciiCharacters")
class CuratedSeedStatementsTest {

    private static final Pattern SHOW_PATTERN = Pattern.compile(
            "INSERT INTO SHOWS .*?VALUES \\((\\d+), .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2})', '([0-9]{4}-[0-9]{2}-[0-9]{2})', .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})',",
            Pattern.DOTALL
    );
    private static final Pattern PERFORMANCE_PATTERN = Pattern.compile(
            "INSERT INTO PERFORMANCES .*?VALUES \\((\\d+), (\\d+), (\\d+), '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})'",
            Pattern.DOTALL
    );
    private static final Pattern PERFORMANCE_SEAT_STATE_PATTERN = Pattern.compile(
            "CASE\\s+WHEN.*'RESERVED'.*ELSE\\s+'AVAILABLE'\\s+END",
            Pattern.DOTALL
    );
    private static final Pattern SHOW_IMAGE_PATTERN = Pattern.compile(
            "INSERT INTO SHOWS .*? '(/api/images/shows/[^']+)'\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*,",
            Pattern.DOTALL
    );

    private static List<String> statements;

    @BeforeAll
    static void parse() {
        statements = CuratedSeedStatements.from(SeedTestPaths.seedSql()).statements();
        assertThat(statements).isNotEmpty();
    }

    @Test
    void 회차가_둘_이상인_공연은_날짜가_하루에_몰리지_않는다() {
        final Map<Long, ShowPeriod> showPeriods = extractShowPeriods(statements);
        final Map<Long, List<LocalDate>> performanceDatesByShow = extractPerformanceDates(statements);

        final Map<Long, List<LocalDate>> singleDaySchedules = new LinkedHashMap<>();
        for (final Map.Entry<Long, List<LocalDate>> entry : performanceDatesByShow.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            final List<LocalDate> uniqueDates = entry.getValue().stream().distinct().sorted().toList();
            if (uniqueDates.size() < 2) {
                singleDaySchedules.put(entry.getKey(), uniqueDates);
            }
        }

        assertThat(singleDaySchedules)
                .withFailMessage("회차가 2개 이상인 공연의 날짜가 하루에만 몰려 있습니다: %s", singleDaySchedules)
                .isEmpty();

        for (final Map.Entry<Long, List<LocalDate>> entry : performanceDatesByShow.entrySet()) {
            final ShowPeriod showPeriod = showPeriods.get(entry.getKey());
            assertThat(showPeriod)
                    .withFailMessage("공연 기간 정보를 찾을 수 없습니다. showId=%s", entry.getKey())
                    .isNotNull();

            assertThat(entry.getValue())
                    .allSatisfy(date -> assertThat(date)
                            .withFailMessage("회차 날짜가 공연 기간을 벗어났습니다. showId=%s, date=%s, period=%s",
                                    entry.getKey(), date, showPeriod)
                            .isBetween(showPeriod.startDate(), showPeriod.endDate()));
        }
    }

    @Test
    void 공연_표시_판매시작이_첫_회차_예매시작과_같다() {
        final Map<Long, ShowPeriod> showPeriods = extractShowPeriods(statements);
        final Map<Long, LocalDateTime> earliestOrderOpenByShow = extractEarliestOrderOpenByShow(statements);

        assertThat(showPeriods.keySet()).containsAll(earliestOrderOpenByShow.keySet());

        earliestOrderOpenByShow.forEach((showId, earliestOrderOpen) ->
                assertThat(showPeriods.get(showId).saleStartDate())
                        .withFailMessage("공연 판매 시작 시각이 첫 회차 예매 시작 시각과 다릅니다. showId=%s, saleStart=%s, earliestOrderOpen=%s",
                                showId, showPeriods.get(showId).saleStartDate(), earliestOrderOpen)
                        .isEqualTo(earliestOrderOpen));
    }

    @Test
    void 공연_표시_판매종료가_마지막_회차_예매마감과_같다() {
        final Map<Long, ShowPeriod> showPeriods = extractShowPeriods(statements);
        final Map<Long, LocalDateTime> latestOrderCloseByShow = extractLatestOrderCloseByShow(statements);

        assertThat(showPeriods.keySet()).containsAll(latestOrderCloseByShow.keySet());

        latestOrderCloseByShow.forEach((showId, latestOrderClose) ->
                assertThat(showPeriods.get(showId).saleEndDate())
                        .withFailMessage("공연 판매 종료 시각이 마지막 회차 예매 마감 시각과 다릅니다. showId=%s, saleEnd=%s, latestOrderClose=%s",
                                showId, showPeriods.get(showId).saleEndDate(), latestOrderClose)
                        .isEqualTo(latestOrderClose));
    }

    @Test
    void 회차좌석_시드는_연속_RESERVED_블록을_만든다() {
        final String performanceSeatStatement = performanceSeatsStatement();

        assertThat(PERFORMANCE_SEAT_STATE_PATTERN.matcher(performanceSeatStatement).find())
                .withFailMessage("PERFORMANCE_SEATS 시드가 AVAILABLE만 고정 생성하고 있습니다.")
                .isTrue();

        assertThat(performanceSeatStatement).contains("CAST(st.seat_no AS INTEGER)");
        assertThat(performanceSeatStatement).contains("SUBSTR(st.section, 1, 1)");
        assertThat(performanceSeatStatement).contains("SUBSTR(st.row_no, 1, 1)");

        for (int base = 0; base < 3; base++) {
            final int blockSize = base + 2;
            final int cycleSize = base + 5;
            final double reservedRatio = (double) blockSize / cycleSize;

            assertThat(reservedRatio)
                    .withFailMessage("연속 RESERVED 비율이 요청 범위(30%%~60%%) 밖입니다. ratio=%.4f", reservedRatio)
                    .isBetween(0.30d, 0.60d);

            assertThat(longestReservedRunLength(base, 20))
                    .withFailMessage("연속 RESERVED 길이가 2~4연석 범위를 벗어났습니다. base=%s", base)
                    .isBetween(2, 4);
        }
    }

    /**
     * PERFORMANCE_SEATS는 {@code @Version} 낙관적 락 컬럼을 NOT NULL로 매핑한다. Hibernate
     * {@code ddl-auto: create}(로컬 프로파일)는 DEFAULT 없이 NOT NULL만 만들므로 시드가 값을 직접
     * 넣어야 한다 — Flyway migration(V3)의 {@code DEFAULT 0}에 의존할 수 없다.
     */
    @Test
    void 회차좌석_시드가_낙관적_락_버전을_직접_채운다() {
        assertThat(performanceSeatsStatement())
                .contains("version, created_at, created_by");
    }

    @Test
    void 회차좌석_시드가_H2에서_실행된다() throws Exception {
        final String performanceSeatStatement = performanceSeatsStatement();

        try (
                Connection connection = DriverManager.getConnection(
                        "jdbc:h2:mem:curated_seed_statements;MODE=Oracle;DB_CLOSE_DELAY=-1");
                Statement statement = connection.createStatement()
        ) {
            statement.execute("CREATE TABLE PERFORMANCES (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE SHOWS (id BIGINT PRIMARY KEY, venue_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE SEATS (id BIGINT PRIMARY KEY, venue_id BIGINT NOT NULL, section VARCHAR(10), row_no VARCHAR(10), seat_no VARCHAR(10))");
            statement.execute("CREATE TABLE GRADES (id BIGINT PRIMARY KEY, code VARCHAR(10))");
            statement.execute("CREATE TABLE PERFORMANCE_GRADES (id BIGINT PRIMARY KEY, performance_id BIGINT NOT NULL, grade_id BIGINT NOT NULL, price NUMBER(10, 0))");
            // 실제 앱 매핑과 같이 version을 NOT NULL(DEFAULT 없음)로 둔다.
            statement.execute("CREATE TABLE PERFORMANCE_SEATS (performance_id BIGINT, seat_id BIGINT, state VARCHAR(20), performance_grade_id BIGINT, unit_price NUMBER(10, 0), version BIGINT NOT NULL, created_at TIMESTAMP, created_by VARCHAR(50))");

            statement.execute("INSERT INTO SHOWS (id, venue_id) VALUES (100, 1)");
            statement.execute("INSERT INTO PERFORMANCES (id, show_id) VALUES (1, 100)");
            statement.execute("INSERT INTO SEATS (id, venue_id, section, row_no, seat_no) VALUES (10, 1, '가', 'A', '1')");
            statement.execute("INSERT INTO GRADES (id, code) VALUES (2000, 'R')");
            statement.execute("INSERT INTO PERFORMANCE_GRADES (id, performance_id, grade_id, price) VALUES (3000, 1, 2000, 120000)");

            statement.executeUpdate(performanceSeatStatement);

            try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM PERFORMANCE_SEATS WHERE version = 0")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void 좌석은_VENUE_1_템플릿을_공연장마다_복제한다() throws Exception {
        final String replicationStatement = statements.stream()
                .filter(statement -> statement.startsWith("INSERT INTO SEATS")
                        && statement.contains("CROSS JOIN VENUES"))
                .findFirst()
                .orElseThrow();

        assertThat(statements)
                .withFailMessage("PERFORMANCE_SEATS 시드가 여전히 venue 경계 없는 CROSS JOIN SEATS를 씁니다.")
                .noneMatch(statement -> statement.contains("CROSS JOIN SEATS"));

        try (
                Connection connection = DriverManager.getConnection(
                        "jdbc:h2:mem:curated_seed_seat_replication;MODE=Oracle;DB_CLOSE_DELAY=-1");
                Statement statement = connection.createStatement()
        ) {
            statement.execute("CREATE TABLE VENUES (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE SEATS (id BIGINT PRIMARY KEY, venue_id BIGINT NOT NULL, section VARCHAR(10), row_no VARCHAR(10), seat_no VARCHAR(10), floor INT, x DOUBLE, y DOUBLE, created_at TIMESTAMP, created_by VARCHAR(50))");

            statement.execute("INSERT INTO VENUES (id) VALUES (1)");
            statement.execute("INSERT INTO VENUES (id) VALUES (2)");
            statement.execute("INSERT INTO VENUES (id) VALUES (3)");
            for (int seatId = 1; seatId <= 600; seatId++) {
                statement.execute("INSERT INTO SEATS (id, venue_id, section, row_no, seat_no, floor, x, y, created_at, created_by) VALUES ("
                        + seatId + ", 1, 'S', 'A', '" + seatId + "', 1, 0, 0, '2026-01-01 10:00:00', 't')");
            }

            statement.executeUpdate(replicationStatement);

            try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM SEATS")) {
                assertThat(resultSet.next()).isTrue();
                // VENUE 3개 * 좌석 600석 = 1800석. VENUE 2/3이 VENUE 1의 template을 그대로 복제한다.
                assertThat(resultSet.getInt(1)).isEqualTo(1800);
            }
            try (var resultSet = statement.executeQuery(
                    "SELECT COUNT(DISTINCT venue_id) FROM SEATS WHERE section = 'S' AND row_no = 'A' AND seat_no = '1'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(3);
            }
        }
    }

    @Test
    void 회차좌석은_그_회차_공연장의_좌석만_조인한다() throws Exception {
        final String performanceSeatsStatement = performanceSeatsStatement();

        try (
                Connection connection = DriverManager.getConnection(
                        "jdbc:h2:mem:curated_seed_performance_seats_venue;MODE=Oracle;DB_CLOSE_DELAY=-1");
                Statement statement = connection.createStatement()
        ) {
            statement.execute("CREATE TABLE SHOWS (id BIGINT PRIMARY KEY, venue_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE PERFORMANCES (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE SEATS (id BIGINT PRIMARY KEY, venue_id BIGINT NOT NULL, section VARCHAR(10), row_no VARCHAR(10), seat_no VARCHAR(10))");
            statement.execute("CREATE TABLE GRADES (id BIGINT PRIMARY KEY, code VARCHAR(10))");
            statement.execute("CREATE TABLE PERFORMANCE_GRADES (id BIGINT PRIMARY KEY, performance_id BIGINT NOT NULL, grade_id BIGINT NOT NULL, price NUMBER(10, 0))");
            statement.execute("CREATE TABLE PERFORMANCE_SEATS (performance_id BIGINT, seat_id BIGINT, state VARCHAR(20), performance_grade_id BIGINT, unit_price NUMBER(10, 0), version BIGINT NOT NULL, created_at TIMESTAMP, created_by VARCHAR(50))");

            statement.execute("INSERT INTO SHOWS (id, venue_id) VALUES (100, 1)");
            statement.execute("INSERT INTO SHOWS (id, venue_id) VALUES (200, 2)");
            statement.execute("INSERT INTO PERFORMANCES (id, show_id) VALUES (1, 100)");
            statement.execute("INSERT INTO PERFORMANCES (id, show_id) VALUES (2, 200)");
            statement.execute("INSERT INTO SEATS (id, venue_id, section, row_no, seat_no) VALUES (10, 1, '나', 'A', '1')");
            statement.execute("INSERT INTO SEATS (id, venue_id, section, row_no, seat_no) VALUES (20, 2, '나', 'A', '1')");
            statement.execute("INSERT INTO GRADES (id, code) VALUES (1000, 'VIP')");
            statement.execute("INSERT INTO PERFORMANCE_GRADES (id, performance_id, grade_id, price) VALUES (10000, 1, 1000, 150000)");
            statement.execute("INSERT INTO PERFORMANCE_GRADES (id, performance_id, grade_id, price) VALUES (20000, 2, 1000, 150000)");

            statement.executeUpdate(performanceSeatsStatement);

            // Performance 1(Show 100)은 VENUE 1의 SEAT(10)만, Performance 2(Show 200)는 VENUE 2의
            // SEAT(20)만 갖는다 — 서로의 VENUE에 속하지 않은 좌석과는 절대 섞이지 않는다.
            try (var resultSet = statement.executeQuery(
                    "SELECT performance_id, seat_id FROM PERFORMANCE_SEATS ORDER BY performance_id")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong("performance_id")).isEqualTo(1L);
                assertThat(resultSet.getLong("seat_id")).isEqualTo(10L);
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong("performance_id")).isEqualTo(2L);
                assertThat(resultSet.getLong("seat_id")).isEqualTo(20L);
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    void 회차_날짜_재작성이_NULL_hold_한도를_보존한다() {
        final String statement = "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES (1, 2, 3, '2026-03-01 19:00:00', '2026-03-01 21:00:00', '2026-02-20 10:00:00', '2026-03-01 20:00:00', NULL, 300, '2026-01-01 10:00:00', 'seed')";

        final String rewritten =
                CuratedSeedStatements.rewritePerformanceStatement(statement, LocalDate.of(2026, 3, 5));

        assertThat(rewritten).contains(", NULL, 300,");
        assertThat(rewritten).contains("'2026-03-05 19:00:00'");
        assertThat(rewritten).contains("'2026-03-05 21:00:00'");
        assertThat(rewritten).contains("'2026-03-05 20:00:00'");
    }

    @Test
    void 회차_INSERT를_일정과_판매정책으로_분리한다() {
        final String combined = "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES (1, 2, 3, '2026-03-01 19:00:00', '2026-03-01 21:00:00', '2026-02-20 10:00:00', '2026-03-01 20:00:00', 4, 600, '2026-01-01 10:00:00', '시드')";
        final String unrelated = "INSERT INTO SHOWS (id) VALUES (1)";

        final List<String> split =
                CuratedSeedStatements.splitPerformancePolicyStatements(List.of(combined, unrelated));

        assertThat(split).hasSize(3);
        final String performanceStatement = split.get(0);
        final String policyStatement = split.get(1);

        assertThat(performanceStatement).startsWith(
                "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, created_at, created_by)");
        assertThat(performanceStatement).doesNotContain("order_open_time");
        assertThat(performanceStatement)
                .contains("'2026-03-01 19:00:00'", "'2026-03-01 21:00:00'", "'2026-01-01 10:00:00'", "'시드'");

        assertThat(policyStatement).startsWith("INSERT INTO BOOKING_PERFORMANCE_SALES_POLICIES");
        assertThat(policyStatement)
                .contains("(1, '2026-02-20 10:00:00', '2026-03-01 20:00:00', 4, 600, 0, '2026-01-01 10:00:00', '시드')");

        assertThat(split.get(2)).isEqualTo(unrelated);
    }

    @Test
    void 실제_실행_문장에는_회차_판매정책이_분리돼_있다() {
        final List<String> executable =
                CuratedSeedStatements.from(SeedTestPaths.seedSql()).executableStatements();

        assertThat(executable)
                .as("실행 문장에 예매 정책 컬럼이 섞인 PERFORMANCES INSERT가 남아 있으면 안 된다")
                .noneMatch(statement -> statement.startsWith("INSERT INTO PERFORMANCES")
                        && statement.contains("order_open_time"));
        assertThat(executable.stream()
                .filter(statement -> statement.startsWith("INSERT INTO BOOKING_PERFORMANCE_SALES_POLICIES"))
                .count())
                .as("회차 리터럴 INSERT마다 판매정책 INSERT가 하나씩 생겨야 한다")
                .isEqualTo(executable.stream()
                        .filter(statement -> statement.startsWith("INSERT INTO PERFORMANCES ("))
                        .count());
    }

    @Test
    void 로컬_공연_이미지에_원본과_카드_에셋이_모두_있다() {
        final Path staticRoot = SeedTestPaths.projectDir()
                .resolve(Path.of("src", "main", "resources", "static"));
        assertThat(staticRoot)
                .withFailMessage("정적 이미지 루트를 찾을 수 없습니다: %s", staticRoot.toAbsolutePath())
                .exists();

        final List<String> localImagePaths = statements.stream()
                .map(CuratedSeedStatementsTest::extractLocalShowImagePath)
                .filter(Objects::nonNull)
                .toList();

        assertThat(localImagePaths).isNotEmpty();

        for (final String imagePath : localImagePaths) {
            final String relativePath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
            final Path originalPath = staticRoot.resolve(relativePath);
            final String fileName = originalPath.getFileName().toString();
            final int extensionIndex = fileName.lastIndexOf('.');
            final String baseName = extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
            final Path cardPath = staticRoot.resolve(Path.of("api", "images", "shows", "card", baseName + ".jpg"));

            assertThat(Files.exists(originalPath))
                    .withFailMessage("원본 공연 이미지가 없습니다: %s", originalPath.toAbsolutePath())
                    .isTrue();
            assertThat(Files.exists(cardPath))
                    .withFailMessage("카드 공연 이미지가 없습니다: %s", cardPath.toAbsolutePath())
                    .isTrue();
        }
    }

    private static String performanceSeatsStatement() {
        return statements.stream()
                .filter(statement -> statement.startsWith("INSERT INTO PERFORMANCE_SEATS"))
                .findFirst()
                .orElseThrow();
    }

    private static int longestReservedRunLength(final int base, final int seatCount) {
        final int blockSize = base + 2;
        final int cycleSize = base + 5;
        int longest = 0;
        int current = 0;

        for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
            final int rotatedIndex = Math.floorMod(seatIndex - base, cycleSize);
            if (rotatedIndex < blockSize) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }

        return longest;
    }

    private static Map<Long, ShowPeriod> extractShowPeriods(final List<String> statements) {
        final Map<Long, ShowPeriod> showPeriods = new HashMap<>();

        for (final String statement : statements) {
            final Matcher matcher = SHOW_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            showPeriods.put(
                    Long.parseLong(matcher.group(1)),
                    new ShowPeriod(
                            LocalDate.parse(matcher.group(2)),
                            LocalDate.parse(matcher.group(3)),
                            LocalDateTime.parse(matcher.group(4).replace(' ', 'T')),
                            LocalDateTime.parse(matcher.group(5).replace(' ', 'T'))
                    )
            );
        }

        return showPeriods;
    }

    private static String extractLocalShowImagePath(final String statement) {
        final Matcher matcher = SHOW_IMAGE_PATTERN.matcher(statement);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Map<Long, List<LocalDate>> extractPerformanceDates(final List<String> statements) {
        final Map<Long, List<PerformanceDate>> performanceDates = new HashMap<>();

        for (final String statement : statements) {
            final Matcher matcher = PERFORMANCE_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            performanceDates.computeIfAbsent(Long.parseLong(matcher.group(2)), ignored -> new ArrayList<>())
                    .add(new PerformanceDate(
                            Integer.parseInt(matcher.group(3)),
                            LocalDateTime.parse(matcher.group(4).replace(' ', 'T')).toLocalDate()));
        }

        final Map<Long, List<LocalDate>> result = new LinkedHashMap<>();
        performanceDates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(PerformanceDate::performanceNo))
                                .map(PerformanceDate::date)
                                .toList()
                ));

        return result;
    }

    private static Map<Long, LocalDateTime> extractEarliestOrderOpenByShow(final List<String> statements) {
        final Map<Long, LocalDateTime> earliest = new HashMap<>();

        for (final String statement : statements) {
            final Matcher matcher = PERFORMANCE_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }
            earliest.merge(
                    Long.parseLong(matcher.group(2)),
                    LocalDateTime.parse(matcher.group(6).replace(' ', 'T')),
                    (current, candidate) -> candidate.isBefore(current) ? candidate : current);
        }

        return earliest;
    }

    private static Map<Long, LocalDateTime> extractLatestOrderCloseByShow(final List<String> statements) {
        final Map<Long, LocalDateTime> latest = new HashMap<>();

        for (final String statement : statements) {
            final Matcher matcher = PERFORMANCE_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }
            latest.merge(
                    Long.parseLong(matcher.group(2)),
                    LocalDateTime.parse(matcher.group(7).replace(' ', 'T')),
                    (current, candidate) -> candidate.isAfter(current) ? candidate : current);
        }

        return latest;
    }

    private record ShowPeriod(
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime saleStartDate,
            LocalDateTime saleEndDate
    ) {
    }

    private record PerformanceDate(int performanceNo, LocalDate date) {
    }
}
