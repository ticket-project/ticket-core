package com.ticket.seed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code seed/sql/kopis-curated.sql}을 실행 가능한 SQL 문 목록으로 바꾼다. DB를 모르는 순수 변환
 * 계층이라 파일만 있으면 단위 테스트할 수 있다.
 *
 * <p>파일을 그대로 실행하는 것으로는 기존 동작을 대체할 수 없다. 다음 두 변환이 적재 직전에
 * 반드시 일어나야 한다.
 *
 * <ol>
 *   <li><b>회차 날짜 다변화</b>({@link #diversifyPerformanceDates}) — 같은 공연의 회차가 하루에
 *       몰려 있으면 날짜를 최대 3일로 흩고, 공연의 종료일·표시 판매기간을 회차 집합에 맞춰
 *       다시 쓴다.</li>
 *   <li><b>회차/판매정책 분리</b>({@link #splitPerformancePolicyStatements}) — 파일의
 *       {@code INSERT INTO PERFORMANCES}는 아직 예매 접수 기간·hold 한도까지 한 줄에 담고 있다
 *       (ADR 0006 A2). 대량 curated 데이터라 파일을 고치는 대신, 실행 직전에
 *       {@code PERFORMANCES}(일정만)와 {@code BOOKING_PERFORMANCE_SALES_POLICIES}(정책) 두
 *       INSERT로 나눈다.</li>
 * </ol>
 *
 * <p><b>문장 순서는 파일 그대로 유지한다.</b> 파일 중간의 {@code SEATS} VENUE별 복제와 끝의
 * {@code GRADES} / {@code PERFORMANCE_GRADES} / {@code PERFORMANCE_SEATS}는
 * {@code INSERT ... SELECT}라 실행 시점에 존재하는 행만 대상으로 삼는다. 순서를 바꾸면 적재
 * 결과 자체가 달라진다.
 */
final class CuratedSeedStatements {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Pattern SHOW_INSERT_PATTERN = Pattern.compile(
            "INSERT INTO SHOWS .*?VALUES \\((\\d+), .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2})', '([0-9]{4}-[0-9]{2}-[0-9]{2})', .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})',",
            Pattern.DOTALL
    );
    private static final Pattern PERFORMANCE_INSERT_PATTERN = Pattern.compile(
            "INSERT INTO PERFORMANCES .*?VALUES \\((\\d+), (\\d+), (\\d+), '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})',\\s*(NULL|\\d+),\\s*(\\d+)(.*)",
            Pattern.DOTALL
    );

    /** 리터럴 한 행 INSERT를 세는 패턴. {@code INSERT ... SELECT}는 걸러진다. */
    private static final Pattern LITERAL_INSERT_PATTERN =
            Pattern.compile("^INSERT INTO ([A-Z_]+) \\([^)]*\\) VALUES \\(", Pattern.DOTALL);

    private final List<String> statements;

    private CuratedSeedStatements(final List<String> statements) {
        this.statements = List.copyOf(statements);
    }

    static CuratedSeedStatements from(final Path sqlPath) {
        return new CuratedSeedStatements(diversifyPerformanceDates(parse(readLines(sqlPath))));
    }

    /** 날짜 다변화까지 끝난 문장 목록이다. 회차/판매정책 분리는 아직 하지 않은 상태다. */
    List<String> statements() {
        return statements;
    }

    /** 실제로 DB에 던지는 문장 목록이다. */
    List<String> executableStatements() {
        return splitPerformancePolicyStatements(statements);
    }

    /** 리터럴 한 행 INSERT 개수다. 적재 완전성 판정의 기대값 원본이다. */
    long literalInsertCount(final String table) {
        return statements.stream()
                .filter(statement -> isLiteralInsertInto(statement, table))
                .count();
    }

    /**
     * {@code SEATS} VENUE별 복제 {@code INSERT ... SELECT}보다 앞에 선언된 {@code VENUES} 행 수다.
     * 복제는 그 시점에 존재하는 VENUES만 대상으로 하므로, 적재 후 SEATS 행 수는
     * {@code 좌석 템플릿 수 x 이 값}이 된다. 파일 뒤쪽에 추가된 VENUES는 좌석을 받지 못한다 —
     * 현재 파일의 실제 상태이며 이 클래스는 그것을 바꾸지 않고 그대로 계산한다.
     */
    long venueCountBeforeSeatReplication() {
        long venues = 0;
        for (final String statement : statements) {
            if (isSeatReplicationStatement(statement)) {
                return venues;
            }
            if (isLiteralInsertInto(statement, "VENUES")) {
                venues++;
            }
        }
        throw new SeedFailure("시드 SQL에서 SEATS VENUE별 복제 INSERT(CROSS JOIN VENUES)를 찾지 못했습니다.");
    }

    private static boolean isSeatReplicationStatement(final String statement) {
        return statement.startsWith("INSERT INTO SEATS") && statement.contains("CROSS JOIN VENUES");
    }

    private static boolean isLiteralInsertInto(final String statement, final String table) {
        final Matcher matcher = LITERAL_INSERT_PATTERN.matcher(statement);
        return matcher.find() && matcher.group(1).equals(table);
    }

    private static List<String> readLines(final Path sqlPath) {
        try {
            return Files.readAllLines(sqlPath, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new SeedFailure("시드 SQL 파일을 읽을 수 없습니다: " + sqlPath, exception);
        }
    }

    private static List<String> parse(final List<String> lines) {
        final List<String> parsed = new ArrayList<>();
        final StringBuilder current = new StringBuilder();

        for (final String line : lines) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }

            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                parsed.add(current.toString().trim().replaceAll(";\\s*$", ""));
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) {
            parsed.add(current.toString().trim());
        }

        return parsed;
    }

    // ---------------------------------------------------------------- 날짜 다변화

    private static List<String> diversifyPerformanceDates(final List<String> parsed) {
        final List<String> diversified = new ArrayList<>(parsed);
        final Map<Long, ShowSeed> shows = extractShows(parsed);
        final Map<Long, List<PerformanceSeed>> performancesByShow = extractPerformances(parsed);

        for (final Map.Entry<Long, List<PerformanceSeed>> entry : performancesByShow.entrySet()) {
            final List<PerformanceSeed> performanceSeeds = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(PerformanceSeed::performanceNo))
                    .toList();
            final ShowSeed showSeed = shows.get(entry.getKey());
            final ShowWindow showWindow = buildShowWindow(performanceSeeds, showSeed, diversified);

            if (showSeed == null) {
                continue;
            }

            if (shouldRewriteShow(showSeed, showWindow)) {
                diversified.set(showSeed.statementIndex(), rewriteShowStatement(showSeed.statement(), showWindow));
            }
        }

        return diversified;
    }

    private static ShowWindow buildShowWindow(
            final List<PerformanceSeed> performanceSeeds,
            final ShowSeed showSeed,
            final List<String> diversified
    ) {
        if (performanceSeeds.size() < 2 || hasMultipleDates(performanceSeeds)) {
            return new ShowWindow(
                    resolveEndDate(showSeed, performanceSeeds),
                    earliestOrderOpenTime(performanceSeeds),
                    latestOrderCloseTime(performanceSeeds)
            );
        }

        final LocalDate firstDate = showSeed == null ? performanceSeeds.get(0).startDate() : showSeed.startDate();
        final int dateBucketCount = Math.min(3, performanceSeeds.size());
        LocalDate lastAssignedDate = firstDate;
        LocalDateTime earliestOrderOpenTime = performanceSeeds.get(0).orderOpenTime();
        LocalDateTime latestOrderCloseTime = performanceSeeds.get(0).orderCloseTime();

        for (int index = 0; index < performanceSeeds.size(); index++) {
            final PerformanceSeed performanceSeed = performanceSeeds.get(index);
            final LocalDate assignedDate = firstDate.plusDays((long) index * dateBucketCount / performanceSeeds.size());
            diversified.set(
                    performanceSeed.statementIndex(),
                    rewritePerformanceStatement(performanceSeed.statement(), assignedDate)
            );
            lastAssignedDate = assignedDate;
            earliestOrderOpenTime = earlierOf(earliestOrderOpenTime, performanceSeed.orderOpenTime());
            latestOrderCloseTime = laterOf(
                    latestOrderCloseTime,
                    assignedDate.atTime(performanceSeed.orderCloseTime().toLocalTime())
            );
        }

        return new ShowWindow(resolveEndDate(showSeed, lastAssignedDate), earliestOrderOpenTime, latestOrderCloseTime);
    }

    private static Map<Long, ShowSeed> extractShows(final List<String> parsed) {
        final Map<Long, ShowSeed> shows = new HashMap<>();

        for (int index = 0; index < parsed.size(); index++) {
            final String statement = parsed.get(index);
            final Matcher matcher = SHOW_INSERT_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            final long showId = Long.parseLong(matcher.group(1));
            shows.put(showId, new ShowSeed(
                    index,
                    statement,
                    LocalDate.parse(matcher.group(2)),
                    LocalDate.parse(matcher.group(3)),
                    LocalDateTime.parse(matcher.group(4) + "T" + matcher.group(5)),
                    LocalDateTime.parse(matcher.group(6) + "T" + matcher.group(7))
            ));
        }

        return shows;
    }

    private static Map<Long, List<PerformanceSeed>> extractPerformances(final List<String> parsed) {
        final Map<Long, List<PerformanceSeed>> performancesByShow = new HashMap<>();

        for (int index = 0; index < parsed.size(); index++) {
            final String statement = parsed.get(index);
            final Matcher matcher = PERFORMANCE_INSERT_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            final long showId = Long.parseLong(matcher.group(2));
            performancesByShow.computeIfAbsent(showId, ignored -> new ArrayList<>())
                    .add(new PerformanceSeed(
                            index,
                            statement,
                            Integer.parseInt(matcher.group(3)),
                            LocalDate.parse(matcher.group(4)),
                            LocalDateTime.parse(matcher.group(8) + "T" + matcher.group(9)),
                            LocalDateTime.parse(matcher.group(10) + "T" + matcher.group(11))
                    ));
        }

        return performancesByShow;
    }

    private static boolean hasMultipleDates(final List<PerformanceSeed> performanceSeeds) {
        final LocalDate firstDate = performanceSeeds.get(0).startDate();
        return performanceSeeds.stream().anyMatch(seed -> !seed.startDate().equals(firstDate));
    }

    private static boolean shouldRewriteShow(final ShowSeed showSeed, final ShowWindow showWindow) {
        return !showSeed.endDate().equals(showWindow.endDate())
                || !showSeed.saleStartDate().equals(showWindow.saleStartDate())
                || !showSeed.saleEndDate().equals(showWindow.saleEndDate());
    }

    private static LocalDate resolveEndDate(final ShowSeed showSeed, final List<PerformanceSeed> performanceSeeds) {
        return resolveEndDate(showSeed, performanceSeeds.get(performanceSeeds.size() - 1).startDate());
    }

    private static LocalDate resolveEndDate(final ShowSeed showSeed, final LocalDate performanceEndDate) {
        if (showSeed == null || performanceEndDate.isAfter(showSeed.endDate())) {
            return performanceEndDate;
        }
        return showSeed.endDate();
    }

    private static LocalDateTime earliestOrderOpenTime(final List<PerformanceSeed> performanceSeeds) {
        LocalDateTime earliest = performanceSeeds.get(0).orderOpenTime();
        for (final PerformanceSeed seed : performanceSeeds) {
            earliest = earlierOf(earliest, seed.orderOpenTime());
        }
        return earliest;
    }

    private static LocalDateTime latestOrderCloseTime(final List<PerformanceSeed> performanceSeeds) {
        LocalDateTime latest = performanceSeeds.get(0).orderCloseTime();
        for (final PerformanceSeed seed : performanceSeeds) {
            latest = laterOf(latest, seed.orderCloseTime());
        }
        return latest;
    }

    private static LocalDateTime earlierOf(final LocalDateTime current, final LocalDateTime candidate) {
        return candidate.isBefore(current) ? candidate : current;
    }

    private static LocalDateTime laterOf(final LocalDateTime current, final LocalDateTime candidate) {
        return candidate.isAfter(current) ? candidate : current;
    }

    private static String rewriteShowStatement(final String statement, final ShowWindow showWindow) {
        final Matcher matcher = SHOW_INSERT_PATTERN.matcher(statement);
        if (!matcher.find()) {
            return statement;
        }

        final StringBuilder builder = new StringBuilder(statement);
        builder.replace(matcher.start(7), matcher.end(7), showWindow.saleEndDate().toLocalTime().format(TIME_FORMATTER));
        builder.replace(matcher.start(6), matcher.end(6), showWindow.saleEndDate().toLocalDate().toString());
        builder.replace(matcher.start(5), matcher.end(5), showWindow.saleStartDate().toLocalTime().format(TIME_FORMATTER));
        builder.replace(matcher.start(4), matcher.end(4), showWindow.saleStartDate().toLocalDate().toString());
        builder.replace(matcher.start(3), matcher.end(3), showWindow.endDate().toString());
        return builder.toString();
    }

    static String rewritePerformanceStatement(final String statement, final LocalDate assignedDate) {
        final Matcher matcher = PERFORMANCE_INSERT_PATTERN.matcher(statement);
        if (!matcher.find()) {
            return statement;
        }

        return "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES ("
                + matcher.group(1) + ", "
                + matcher.group(2) + ", "
                + matcher.group(3) + ", '"
                + assignedDate + " " + matcher.group(5) + "', '"
                + assignedDate + " " + matcher.group(7) + "', '"
                + matcher.group(8) + " " + matcher.group(9) + "', '"
                + assignedDate + " " + matcher.group(11) + "'"
                + ", "
                + matcher.group(12)
                + ", "
                + matcher.group(13)
                + matcher.group(14);
    }

    // -------------------------------------------------- 회차 / 판매정책 분리

    static List<String> splitPerformancePolicyStatements(final List<String> parsed) {
        final List<String> result = new ArrayList<>(parsed.size());
        for (final String statement : parsed) {
            final Matcher matcher = PERFORMANCE_INSERT_PATTERN.matcher(statement);
            if (!matcher.find()) {
                result.add(statement);
                continue;
            }
            result.add(toScheduleOnlyPerformanceStatement(matcher));
            result.add(toSalesPolicyStatement(matcher));
        }
        return result;
    }

    private static String toScheduleOnlyPerformanceStatement(final Matcher matcher) {
        return "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, created_at, created_by) VALUES ("
                + matcher.group(1) + ", "
                + matcher.group(2) + ", "
                + matcher.group(3) + ", '"
                + matcher.group(4) + " " + matcher.group(5) + "', '"
                + matcher.group(6) + " " + matcher.group(7) + "'"
                + matcher.group(14);
    }

    private static String toSalesPolicyStatement(final Matcher matcher) {
        return "INSERT INTO BOOKING_PERFORMANCE_SALES_POLICIES (performance_id, order_opens_at, order_closes_at, max_hold_seat_count, hold_duration_seconds, version, created_at, created_by) VALUES ("
                + matcher.group(1) + ", '"
                + matcher.group(8) + " " + matcher.group(9) + "', '"
                + matcher.group(10) + " " + matcher.group(11) + "', "
                + matcher.group(12) + ", "
                + matcher.group(13) + ", 0"
                + matcher.group(14);
    }

    private record ShowSeed(
            int statementIndex,
            String statement,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime saleStartDate,
            LocalDateTime saleEndDate
    ) {
    }

    private record PerformanceSeed(
            int statementIndex,
            String statement,
            int performanceNo,
            LocalDate startDate,
            LocalDateTime orderOpenTime,
            LocalDateTime orderCloseTime
    ) {
    }

    private record ShowWindow(LocalDate endDate, LocalDateTime saleStartDate, LocalDateTime saleEndDate) {
    }
}
