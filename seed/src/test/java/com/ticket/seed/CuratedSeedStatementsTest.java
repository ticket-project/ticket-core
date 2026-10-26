package com.ticket.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code seed/sql/kopis-curated.sql}이 지켜야 할 불변식을 고정한다. 회차 날짜 분산과 공연 표시 기간은 파일에 이미 반영돼 있고, 새 수집분은
 * {@code seed/kopis/fetch-kopis.mjs}가 같은 규칙으로 만든다.
 */
@SuppressWarnings("NonAsciiCharacters")
class CuratedSeedStatementsTest {

    private static final Pattern SHOW_PATTERN = Pattern.compile(
            "INSERT INTO SHOWS .*?VALUES \\((\\d+), .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2})', '([0-9]{4}-[0-9]{2}-[0-9]{2})', .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})',",
            Pattern.DOTALL);
    private static final Pattern PERFORMANCE_PATTERN = Pattern.compile(
            "INSERT INTO PERFORMANCES .*?VALUES \\((\\d+), (\\d+), \\d+, '([0-9]{4}-[0-9]{2}-[0-9]{2}) [0-9:]{8}'",
            Pattern.DOTALL);
    private static final Pattern POLICY_PATTERN = Pattern.compile(
            "INSERT INTO BOOKING_PERFORMANCE_SALES_POLICIES .*?VALUES \\((\\d+), '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]{8})'",
            Pattern.DOTALL);
    private static final Pattern SHOW_IMAGE_PATTERN = Pattern.compile(
            "INSERT INTO SHOWS .*? '(/api/images/shows/[^']+)'\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*,",
            Pattern.DOTALL);

    private static List<String> statements;
    private static Map<Long, ShowPeriod> shows;
    private static Map<Long, List<PerformanceSeed>> performancesByShow;

    @BeforeAll
    static void parse() {
        statements = CuratedSeedStatements.from(SeedTestPaths.seedSql()).statements();
        shows = extractShows();
        performancesByShow = extractPerformances();
        assertThat(performancesByShow).isNotEmpty();
    }

    @Test
    void 회차가_둘_이상인_공연은_날짜가_하루에_몰리지_않고_공연_기간_안에_있다() {
        performancesByShow.forEach((showId, performances) -> {
            final ShowPeriod show = shows.get(showId);
            assertThat(show).as("showId=%s", showId).isNotNull();
            if (performances.size() >= 2) {
                assertThat(performances.stream().map(PerformanceSeed::date).distinct())
                        .as("회차 날짜가 하루에만 몰려 있다. showId=%s", showId)
                        .hasSizeGreaterThan(1);
            }
            assertThat(performances)
                    .allSatisfy(performance -> assertThat(performance.date())
                            .as("회차 날짜가 공연 기간을 벗어났다. showId=%s", showId)
                            .isBetween(show.startDate(), show.endDate()));
        });
    }

    @Test
    void 공연_표시_판매기간은_회차_접수기간을_덮는다() {
        performancesByShow.forEach((showId, performances) -> {
            final ShowPeriod show = shows.get(showId);
            assertThat(show.saleStart())
                    .as("공연 판매 시작은 첫 회차 접수 시작과 같다. showId=%s", showId)
                    .isEqualTo(performances.stream()
                            .map(PerformanceSeed::orderOpen)
                            .min(LocalDateTime::compareTo)
                            .orElseThrow());
            assertThat(show.saleEnd())
                    .as("공연 판매 종료는 마지막 회차 접수 마감과 같다. showId=%s", showId)
                    .isEqualTo(performances.stream()
                            .map(PerformanceSeed::orderClose)
                            .max(LocalDateTime::compareTo)
                            .orElseThrow());
        });
    }

    @Test
    void 집합_기반_INSERT_뒤의_리터럴_INSERT는_적재_전에_막는다(@TempDir final Path directory) throws Exception {
        final Path sql = directory.resolve("late-venue.sql");
        Files.writeString(sql, """
                INSERT INTO VENUES (id) VALUES (1);
                INSERT INTO SEATS (id, venue_id) SELECT t.id, v.id FROM SEATS t CROSS JOIN VENUES v;
                INSERT INTO VENUES (id) VALUES (2);
                """);

        assertThatThrownBy(() -> CuratedSeedStatements.from(sql))
                .isInstanceOf(SeedFailure.class)
                .hasMessageContaining("@seed-splice");
    }

    @Test
    void 로컬_공연_이미지에_원본과_카드_에셋이_모두_있다() {
        final Path staticRoot = SeedTestPaths.projectDir().resolve(Path.of("src", "main", "resources", "static"));
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

    private static Map<Long, ShowPeriod> extractShows() {
        final Map<Long, ShowPeriod> result = new HashMap<>();
        for (final String statement : statements) {
            final Matcher matcher = SHOW_PATTERN.matcher(statement);
            if (matcher.find()) {
                result.put(
                        Long.parseLong(matcher.group(1)),
                        new ShowPeriod(
                                LocalDate.parse(matcher.group(2)),
                                LocalDate.parse(matcher.group(3)),
                                dateTime(matcher.group(4)),
                                dateTime(matcher.group(5))));
            }
        }
        return result;
    }

    /** 회차와 그 회차의 판매정책(접수 기간)을 회차 id로 잇는다. */
    private static Map<Long, List<PerformanceSeed>> extractPerformances() {
        final Map<Long, LocalDateTime[]> policies = new HashMap<>();
        for (final String statement : statements) {
            final Matcher matcher = POLICY_PATTERN.matcher(statement);
            if (matcher.find()) {
                policies.put(
                        Long.parseLong(matcher.group(1)),
                        new LocalDateTime[] {dateTime(matcher.group(2)), dateTime(matcher.group(3))});
            }
        }

        final Map<Long, List<PerformanceSeed>> result = new LinkedHashMap<>();
        for (final String statement : statements) {
            final Matcher matcher = PERFORMANCE_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }
            final long performanceId = Long.parseLong(matcher.group(1));
            final LocalDateTime[] window = policies.get(performanceId);
            assertThat(window)
                    .as("회차마다 판매정책이 있어야 한다. performanceId=%s", performanceId)
                    .isNotNull();
            result.computeIfAbsent(Long.parseLong(matcher.group(2)), ignored -> new ArrayList<>())
                    .add(new PerformanceSeed(LocalDate.parse(matcher.group(3)), window[0], window[1]));
        }
        return result;
    }

    private static LocalDateTime dateTime(final String value) {
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    private static String extractLocalShowImagePath(final String statement) {
        final Matcher matcher = SHOW_IMAGE_PATTERN.matcher(statement);
        return matcher.find() ? matcher.group(1) : null;
    }

    private record ShowPeriod(LocalDate startDate, LocalDate endDate, LocalDateTime saleStart, LocalDateTime saleEnd) {}

    private record PerformanceSeed(LocalDate date, LocalDateTime orderOpen, LocalDateTime orderClose) {}
}
