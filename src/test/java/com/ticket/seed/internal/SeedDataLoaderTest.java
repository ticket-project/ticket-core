package com.ticket.core.infra.seed;

import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.model.Role;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedDataLoaderTest {

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

    @Test
    void multi_round_shows_use_multiple_dates() throws Exception {
        final List<String> statements = parseStatements();
        final Map<Long, ShowPeriod> showPeriods = extractShowPeriods(statements);
        final Map<Long, List<LocalDate>> performanceDatesByShow = extractPerformanceDates(statements);

        final Map<Long, List<LocalDate>> singleDaySchedules = new LinkedHashMap<>();

        for (Map.Entry<Long, List<LocalDate>> entry : performanceDatesByShow.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }

            final List<LocalDate> uniqueDates = entry.getValue().stream()
                .distinct()
                .sorted()
                .toList();

            if (uniqueDates.size() < 2) {
                singleDaySchedules.put(entry.getKey(), uniqueDates);
            }
        }

        assertThat(singleDaySchedules)
            .withFailMessage("회차가 2개 이상인 공연의 날짜가 하루에만 몰려 있습니다: %s", singleDaySchedules)
            .isEmpty();

        for (Map.Entry<Long, List<LocalDate>> entry : performanceDatesByShow.entrySet()) {
            final ShowPeriod showPeriod = showPeriods.get(entry.getKey());
            assertThat(showPeriod)
                .withFailMessage("공연 기간 정보를 찾을 수 없습니다. showId=%s", entry.getKey())
                .isNotNull();

            assertThat(entry.getValue())
                .allSatisfy(date -> assertThat(date)
                    .withFailMessage("회차 날짜가 공연 기간을 벗어났습니다. showId=%s, date=%s, period=%s", entry.getKey(), date, showPeriod)
                    .isBetween(showPeriod.startDate(), showPeriod.endDate()));
        }
    }

    @Test
    void show_sale_start_matches_earliest_performance_order_open_time() throws Exception {
        final List<String> statements = parseStatements();
        final Map<Long, ShowPeriod> showPeriods = extractShowPeriods(statements);
        final Map<Long, LocalDateTime> earliestOrderOpenByShow = extractEarliestOrderOpenByShow(statements);

        assertThat(showPeriods.keySet()).containsAll(earliestOrderOpenByShow.keySet());

        earliestOrderOpenByShow.forEach((showId, earliestOrderOpen) ->
            assertThat(showPeriods.get(showId).saleStartDate())
                .withFailMessage("공연 판매 시작 시각이 첫 회차 예매 시작 시각과 다릅니다. showId=%s, saleStart=%s, earliestOrderOpen=%s",
                    showId, showPeriods.get(showId).saleStartDate(), earliestOrderOpen)
                .isEqualTo(earliestOrderOpen)
        );
    }

    @Test
    void show_sale_end_matches_latest_performance_order_close_time() throws Exception {
        final List<String> statements = parseStatements();
        final Map<Long, ShowPeriod> showPeriods = extractShowPeriods(statements);
        final Map<Long, LocalDateTime> latestOrderCloseByShow = extractLatestOrderCloseByShow(statements);

        assertThat(showPeriods.keySet()).containsAll(latestOrderCloseByShow.keySet());

        latestOrderCloseByShow.forEach((showId, latestOrderClose) ->
            assertThat(showPeriods.get(showId).saleEndDate())
                .withFailMessage("공연 판매 종료 시각이 마지막 회차 예매 마감 시각과 다릅니다. showId=%s, saleEnd=%s, latestOrderClose=%s",
                    showId, showPeriods.get(showId).saleEndDate(), latestOrderClose)
                .isEqualTo(latestOrderClose)
        );
    }

    @Test
    void performance_seat_seed_creates_contiguous_reserved_blocks() throws Exception {
        final String performanceSeatStatement = parseStatements().stream()
            .filter(statement -> statement.startsWith("INSERT INTO PERFORMANCE_SEATS"))
            .findFirst()
            .orElseThrow();

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

    @Test
    void performance_seat_seed_statement_executes_on_h2() throws Exception {
        final String performanceSeatStatement = parseStatements().stream()
            .filter(statement -> statement.startsWith("INSERT INTO PERFORMANCE_SEATS"))
            .findFirst()
            .orElseThrow();

        try (
            Connection connection = DriverManager.getConnection("jdbc:h2:mem:seed_loader_test;MODE=Oracle;DB_CLOSE_DELAY=-1");
            Statement statement = connection.createStatement()
        ) {
            statement.execute("CREATE TABLE PERFORMANCES (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE SEATS (id BIGINT PRIMARY KEY, section VARCHAR(10), row_no VARCHAR(10), seat_no VARCHAR(10))");
            statement.execute("CREATE TABLE SHOW_GRADES (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL, grade_code VARCHAR(10), price NUMBER(10, 0))");
            statement.execute("CREATE TABLE PERFORMANCE_SEATS (performance_id BIGINT, seat_id BIGINT, state VARCHAR(20), price NUMBER(10, 0), created_at TIMESTAMP, created_by VARCHAR(50))");

            statement.execute("INSERT INTO PERFORMANCES (id, show_id) VALUES (1, 100)");
            statement.execute("INSERT INTO SEATS (id, section, row_no, seat_no) VALUES (10, '가', 'A', '1')");
            statement.execute("INSERT INTO SHOW_GRADES (id, show_id, grade_code, price) VALUES (1000, 100, 'R', 120000)");

            statement.executeUpdate(performanceSeatStatement);

            try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM PERFORMANCE_SEATS")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void rewrite_performance_statement_preserves_null_max_can_hold_count() throws Exception {
        final SeedDataLoader loader = new SeedDataLoader(null, null, null);
        final Method method = SeedDataLoader.class.getDeclaredMethod("rewritePerformanceStatement", String.class, LocalDate.class);
        method.setAccessible(true);

        final String statement = "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES (1, 2, 3, '2026-03-01 19:00:00', '2026-03-01 21:00:00', '2026-02-20 10:00:00', '2026-03-01 20:00:00', NULL, 300, '2026-01-01 10:00:00', 'seed')";

        final String rewritten = (String) method.invoke(loader, statement, LocalDate.of(2026, 3, 5));

        assertThat(rewritten).contains(", NULL, 300,");
        assertThat(rewritten).contains("'2026-03-05 19:00:00'");
        assertThat(rewritten).contains("'2026-03-05 21:00:00'");
        assertThat(rewritten).contains("'2026-03-05 20:00:00'");
    }



    @Test
    void local_show_images_have_original_and_card_assets() throws Exception {
        final Path staticRoot = resolveStaticRoot();
        assertThat(staticRoot)
            .withFailMessage("정적 이미지 루트를 찾을 수 없습니다: %s", staticRoot.toAbsolutePath())
            .exists();

        final List<String> localImagePaths = parseStatements().stream()
            .map(this::extractLocalShowImagePath)
            .filter(Objects::nonNull)
            .toList();

        assertThat(localImagePaths).isNotEmpty();

        for (String imagePath : localImagePaths) {
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

    private int longestReservedRunLength(final int base, final int seatCount) {
        final int blockSize = base + 2;
        final int cycleSize = base + 5;
        final int offset = base;
        int longest = 0;
        int current = 0;

        for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
            final int rotatedIndex = Math.floorMod(seatIndex - offset, cycleSize);
            final boolean reserved = rotatedIndex < blockSize;
            if (reserved) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }

        return longest;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStatements() throws Exception {
        final SeedDataLoader loader = new SeedDataLoader(null, null, null);
        final Method method = SeedDataLoader.class.getDeclaredMethod("parseStatements");
        method.setAccessible(true);
        return (List<String>) method.invoke(loader);
    }

    private Map<Long, ShowPeriod> extractShowPeriods(final List<String> statements) {
        final Map<Long, ShowPeriod> showPeriods = new HashMap<>();

        for (String statement : statements) {
            final Matcher matcher = SHOW_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            final long showId = Long.parseLong(matcher.group(1));
            showPeriods.put(
                showId,
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

    private String extractLocalShowImagePath(final String statement) {
        final Matcher matcher = SHOW_IMAGE_PATTERN.matcher(statement);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private Path resolveStaticRoot() {
        // 정적 이미지는 HTTP로 서빙하므로 core-api가 소유한다. 시드 SQL은 그 경로를 참조한다.
        final List<Path> candidates = List.of(
            Path.of("..", "core-api", "src", "main", "resources", "static"),
            Path.of("core", "core-api", "src", "main", "resources", "static"),
            Path.of("src", "main", "resources", "static")
        );

        return candidates.stream()
            .filter(Files::exists)
            .findFirst()
            .orElse(candidates.get(0));
    }

    private Map<Long, List<LocalDate>> extractPerformanceDates(final List<String> statements) {
        final Map<Long, List<PerformanceDate>> performanceDates = new HashMap<>();

        for (String statement : statements) {
            final Matcher matcher = PERFORMANCE_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            final long showId = Long.parseLong(matcher.group(2));
            final int performanceNo = Integer.parseInt(matcher.group(3));
            final LocalDate performanceDate = LocalDateTime.parse(matcher.group(4).replace(' ', 'T')).toLocalDate();

            performanceDates.computeIfAbsent(showId, ignored -> new ArrayList<>())
                .add(new PerformanceDate(performanceNo, performanceDate));
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

    private Map<Long, LocalDateTime> extractEarliestOrderOpenByShow(final List<String> statements) {
        final Map<Long, LocalDateTime> earliestOrderOpenByShow = new HashMap<>();

        for (String statement : statements) {
            final Matcher matcher = PERFORMANCE_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            final long showId = Long.parseLong(matcher.group(2));
            final LocalDateTime orderOpenTime = LocalDateTime.parse(matcher.group(6).replace(' ', 'T'));
            earliestOrderOpenByShow.merge(showId, orderOpenTime, this::earlierTime);
        }

        return earliestOrderOpenByShow;
    }

    private Map<Long, LocalDateTime> extractLatestOrderCloseByShow(final List<String> statements) {
        final Map<Long, LocalDateTime> latestOrderCloseByShow = new HashMap<>();

        for (String statement : statements) {
            final Matcher matcher = PERFORMANCE_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            final long showId = Long.parseLong(matcher.group(2));
            final LocalDateTime orderCloseTime = LocalDateTime.parse(matcher.group(7).replace(' ', 'T'));
            latestOrderCloseByShow.merge(showId, orderCloseTime, this::laterTime);
        }

        return latestOrderCloseByShow;
    }

    private LocalDateTime laterTime(final LocalDateTime current, final LocalDateTime candidate) {
        return candidate.isAfter(current) ? candidate : current;
    }

    private LocalDateTime earlierTime(final LocalDateTime current, final LocalDateTime candidate) {
        return candidate.isBefore(current) ? candidate : current;
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
