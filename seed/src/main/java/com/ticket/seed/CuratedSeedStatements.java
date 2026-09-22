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
 * {@code seed/sql/kopis-curated.sql}을 실행 가능한 SQL 문 목록으로 바꾼다. DB를 모르는 순수 변환 계층이라 파일만 있으면 단위 테스트할 수 있다.
 *
 * <p>파일을 그대로 실행하는 것으로는 기존 동작을 대체할 수 없다. 다음 두 변환이 적재 직전에 반드시 일어나야 한다.
 *
 * <ol>
 *   <li><b>회차 날짜 다변화</b>({@link #diversifyPerformanceDates}) — 같은 공연의 회차가 하루에 몰려 있으면 날짜를 최대 3일로 흩고, 공연의 종료일·표시 판매기간을 회차
 *       집합에 맞춰 다시 쓴다.
 *   <li><b>회차/판매정책 분리</b>({@link #splitPerformancePolicyStatements}) — 파일의 {@code INSERT INTO PERFORMANCES}는 아직 예매 접수
 *       기간·hold 한도까지 한 줄에 담고 있다 (ADR 0006 A2). 대량 curated 데이터라 파일을 고치는 대신, 실행 직전에 {@code PERFORMANCES}(일정만)와
 *       {@code BOOKING_PERFORMANCE_SALES_POLICIES}(정책) 두 INSERT로 나눈다.
 * </ol>
 *
 * <p><b>문장 순서는 파일 그대로 유지한다.</b> 파일 중간의 {@code SEATS} VENUE별 복제와 끝의 {@code GRADES} / {@code PERFORMANCE_GRADES} /
 * {@code PERFORMANCE_SEATS}는 {@code INSERT ... SELECT}라 실행 시점에 존재하는 행만 대상으로 삼는다. 순서를 바꾸면 적재 결과 자체가 달라진다. 그래서
 * {@link #verifyStatementOrder()}가 파싱 직후에 순서를 검사한다 — 좌석 복제 뒤에 선언된 공연장은 좌석을 하나도 받지 못하고, 회차좌석 생성 뒤에 선언된 회차는 등급·가격·좌석을 하나도
 * 받지 못한다.
 *
 * <p>실행 문장에는 한 가지 변환이 더 붙는다({@link #withExplicitDateLiterals}). 날짜·시각 리터럴을 {@code DATE '...'} / {@code TIMESTAMP
 * '...'}로 감싸 세션 기본 날짜 형식({@code NLS_DATE_FORMAT})에 의존하지 않게 한다. Oracle은 그 설정이 다르면 같은 문자열을 다르게 읽거나 아예 실패한다.
 */
final class CuratedSeedStatements {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Pattern SHOW_INSERT_PATTERN = Pattern.compile(
            "INSERT INTO SHOWS .*?VALUES \\((\\d+), .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2})', '([0-9]{4}-[0-9]{2}-[0-9]{2})', .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})',",
            Pattern.DOTALL);
    private static final Pattern PERFORMANCE_INSERT_PATTERN = Pattern.compile(
            "INSERT INTO PERFORMANCES .*?VALUES \\((\\d+), (\\d+), (\\d+), '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})',\\s*(NULL|\\d+),\\s*(\\d+)(.*)",
            Pattern.DOTALL);

    /** 리터럴 한 행 INSERT를 세는 패턴. {@code INSERT ... SELECT}는 걸러진다. */
    private static final Pattern LITERAL_INSERT_PATTERN =
            Pattern.compile("^INSERT INTO ([A-Z_]+) \\([^)]*\\) VALUES \\(", Pattern.DOTALL);

    /** {@code 'YYYY-MM-DD'} 리터럴. 뒤에 시각이 붙지 않은 순수 날짜만 잡는다. */
    private static final Pattern DATE_LITERAL_PATTERN = Pattern.compile("'([0-9]{4}-[0-9]{2}-[0-9]{2})'");

    /** {@code 'YYYY-MM-DD HH:MM:SS'} 리터럴. */
    private static final Pattern TIMESTAMP_LITERAL_PATTERN =
            Pattern.compile("'([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2})'");

    private final List<String> statements;

    private CuratedSeedStatements(final List<String> statements) {
        this.statements = List.copyOf(statements);
    }

    static CuratedSeedStatements from(final Path sqlPath) {
        final CuratedSeedStatements parsed =
                new CuratedSeedStatements(diversifyPerformanceDates(parse(readLines(sqlPath))));
        parsed.verifyStatementOrder();
        return parsed;
    }

    /** 날짜 다변화까지 끝난 문장 목록이다. 회차/판매정책 분리와 날짜 리터럴 변환은 아직 하지 않은 상태다. */
    List<String> statements() {
        return statements;
    }

    /** 실제로 DB에 던지는 문장 목록이다. */
    List<String> executableStatements() {
        return withExplicitDateLiterals(splitPerformancePolicyStatements(statements));
    }

    /**
     * 집합 기반 {@code INSERT ... SELECT}보다 뒤에 선언돼 그 대상에서 빠지는 리터럴 INSERT가 없는지 확인한다.
     *
     * <p>파일 끝에 새 수집분을 이어 붙이면 이 순서가 조용히 깨진다. 실제로 그렇게 추가된 공연장 179개가 좌석을 하나도 받지 못했고, 그 공연장의 공연 198개·회차 662개에 회차좌석이 생성되지
     * 않았다. 적재가 끝난 뒤 개수로 알아채는 것보다 여기서 먼저 막는 것이 낫다.
     */
    void verifyStatementOrder() {
        final int seatReplicationIndex = indexOfSeatReplication();
        final List<String> lateVenueTables = new ArrayList<>();
        for (final String table : List.of("VENUES", "SHOWS")) {
            if (lastLiteralInsertIndex(table) > seatReplicationIndex) {
                lateVenueTables.add(table);
            }
        }
        if (!lateVenueTables.isEmpty()) {
            throw new SeedFailure("""
                    시드 SQL의 문장 순서가 어긋났습니다. 좌석 복제(CROSS JOIN VENUES) 뒤에 선언된 리터럴 INSERT가 있습니다.
                      늦게 선언된 테이블: %s
                      -> 좌석 복제는 실행 시점에 존재하는 VENUES만 대상으로 합니다. 뒤에 선언된 공연장은
                         물리 좌석을 하나도 받지 못합니다. 해당 INSERT를 '-- @seed-splice: venues'
                         마커 앞으로 옮기세요.
                    """.formatted(String.join(", ", lateVenueTables)));
        }

        final int performanceSeatIndex = indexOfPerformanceSeatGeneration();
        if (lastLiteralInsertIndex("PERFORMANCES") > performanceSeatIndex) {
            throw new SeedFailure("""
                    시드 SQL의 문장 순서가 어긋났습니다. 회차좌석 생성(INSERT INTO PERFORMANCE_SEATS ... SELECT) 뒤에
                    선언된 PERFORMANCES 리터럴 INSERT가 있습니다.
                      -> 그 회차는 등급·가격·회차좌석을 하나도 받지 못합니다. 해당 INSERT를
                         '-- @seed-splice: performances' 마커 앞으로 옮기세요.
                    """);
        }
    }

    /** 리터럴 한 행 INSERT 개수다. 적재 완전성 판정의 기대값 원본이다. */
    long literalInsertCount(final String table) {
        return statements.stream()
                .filter(statement -> isLiteralInsertInto(statement, table))
                .count();
    }

    /**
     * {@code SEATS} VENUE별 복제 {@code INSERT ... SELECT}보다 앞에 선언된 {@code VENUES} 행 수다. 복제는 그 시점에 존재하는 VENUES만 대상으로 하므로,
     * 적재 후 SEATS 행 수는 {@code 좌석 템플릿 수 x 이 값}이 된다.
     *
     * <p>{@link #verifyStatementOrder()}가 통과했다면 이 값은 파일 전체의 {@code VENUES} 리터럴 수와 같다 — 좌석을 받지 못하는 공연장이 없다는 뜻이다.
     */
    long venueCountBeforeSeatReplication() {
        final int seatReplicationIndex = indexOfSeatReplication();
        long venues = 0;
        for (int index = 0; index < seatReplicationIndex; index++) {
            if (isLiteralInsertInto(statements.get(index), "VENUES")) {
                venues++;
            }
        }
        return venues;
    }

    private int indexOfSeatReplication() {
        for (int index = 0; index < statements.size(); index++) {
            if (isSeatReplicationStatement(statements.get(index))) {
                return index;
            }
        }
        throw new SeedFailure("시드 SQL에서 SEATS VENUE별 복제 INSERT(CROSS JOIN VENUES)를 찾지 못했습니다.");
    }

    private int indexOfPerformanceSeatGeneration() {
        for (int index = 0; index < statements.size(); index++) {
            final String statement = statements.get(index);
            if (statement.startsWith("INSERT INTO PERFORMANCE_SEATS")
                    && !isLiteralInsertInto(statement, "PERFORMANCE_SEATS")) {
                return index;
            }
        }
        throw new SeedFailure("시드 SQL에서 회차좌석 생성 INSERT(INSERT INTO PERFORMANCE_SEATS ... SELECT)를 찾지 못했습니다.");
    }

    private int lastLiteralInsertIndex(final String table) {
        for (int index = statements.size() - 1; index >= 0; index--) {
            if (isLiteralInsertInto(statements.get(index), table)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isSeatReplicationStatement(final String statement) {
        return statement.startsWith("INSERT INTO SEATS") && statement.contains("CROSS JOIN VENUES");
    }

    // ------------------------------------------------- 명시적 날짜·시각 리터럴

    /**
     * {@code '2026-06-01'} / {@code '2026-06-01 14:00:00'} 문자열 리터럴을 {@code DATE '...'} / {@code TIMESTAMP '...'}로 바꾼다.
     *
     * <p>Oracle은 문자열을 날짜로 바꿀 때 세션의 {@code NLS_DATE_FORMAT}을 쓴다. 기본 형식이 {@code DD-MON-RR}인 세션에서 이 시드를 그대로 실행하면 같은 파일이 다른
     * 결과를 내거나 {@code ORA-01861}로 실패한다. 명시적 리터럴은 그 설정과 무관하게 ISO 형식으로 해석된다.
     *
     * <p>대상은 <b>따옴표 안이 날짜·시각 형식 전체와 정확히 일치하는</b> 리터럴뿐이다. 현재 파일에서 그런 리터럴은 전부 날짜·시각 컬럼 값이다(순수 날짜 784개 = {@code SHOWS}
     * 392행의 {@code start_date}/{@code end_date}).
     */
    static List<String> withExplicitDateLiterals(final List<String> parsed) {
        final List<String> result = new ArrayList<>(parsed.size());
        for (final String statement : parsed) {
            final String withTimestamps =
                    TIMESTAMP_LITERAL_PATTERN.matcher(statement).replaceAll("TIMESTAMP '$1'");
            result.add(DATE_LITERAL_PATTERN.matcher(withTimestamps).replaceAll("DATE '$1'"));
        }
        return result;
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
            final List<PerformanceSeed> performanceSeeds, final ShowSeed showSeed, final List<String> diversified) {
        if (performanceSeeds.size() < 2 || hasMultipleDates(performanceSeeds)) {
            return new ShowWindow(
                    resolveEndDate(showSeed, performanceSeeds),
                    earliestOrderOpenTime(performanceSeeds),
                    latestOrderCloseTime(performanceSeeds));
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
                    rewritePerformanceStatement(performanceSeed.statement(), assignedDate));
            lastAssignedDate = assignedDate;
            earliestOrderOpenTime = earlierOf(earliestOrderOpenTime, performanceSeed.orderOpenTime());
            latestOrderCloseTime = laterOf(
                    latestOrderCloseTime,
                    assignedDate.atTime(performanceSeed.orderCloseTime().toLocalTime()));
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
            shows.put(
                    showId,
                    new ShowSeed(
                            index,
                            statement,
                            LocalDate.parse(matcher.group(2)),
                            LocalDate.parse(matcher.group(3)),
                            LocalDateTime.parse(matcher.group(4) + "T" + matcher.group(5)),
                            LocalDateTime.parse(matcher.group(6) + "T" + matcher.group(7))));
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
            performancesByShow
                    .computeIfAbsent(showId, ignored -> new ArrayList<>())
                    .add(new PerformanceSeed(
                            index,
                            statement,
                            Integer.parseInt(matcher.group(3)),
                            LocalDate.parse(matcher.group(4)),
                            LocalDateTime.parse(matcher.group(8) + "T" + matcher.group(9)),
                            LocalDateTime.parse(matcher.group(10) + "T" + matcher.group(11))));
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
        return resolveEndDate(
                showSeed, performanceSeeds.get(performanceSeeds.size() - 1).startDate());
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
        builder.replace(
                matcher.start(7),
                matcher.end(7),
                showWindow.saleEndDate().toLocalTime().format(TIME_FORMATTER));
        builder.replace(
                matcher.start(6),
                matcher.end(6),
                showWindow.saleEndDate().toLocalDate().toString());
        builder.replace(
                matcher.start(5),
                matcher.end(5),
                showWindow.saleStartDate().toLocalTime().format(TIME_FORMATTER));
        builder.replace(
                matcher.start(4),
                matcher.end(4),
                showWindow.saleStartDate().toLocalDate().toString());
        builder.replace(matcher.start(3), matcher.end(3), showWindow.endDate().toString());
        return builder.toString();
    }

    static String rewritePerformanceStatement(final String statement, final LocalDate assignedDate) {
        final Matcher matcher = PERFORMANCE_INSERT_PATTERN.matcher(statement);
        if (!matcher.find()) {
            return statement;
        }

        return "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES ("
                + matcher.group(1)
                + ", "
                + matcher.group(2)
                + ", "
                + matcher.group(3)
                + ", '"
                + assignedDate
                + " "
                + matcher.group(5)
                + "', '"
                + assignedDate
                + " "
                + matcher.group(7)
                + "', '"
                + matcher.group(8)
                + " "
                + matcher.group(9)
                + "', '"
                + assignedDate
                + " "
                + matcher.group(11)
                + "'"
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
                + matcher.group(1)
                + ", "
                + matcher.group(2)
                + ", "
                + matcher.group(3)
                + ", '"
                + matcher.group(4)
                + " "
                + matcher.group(5)
                + "', '"
                + matcher.group(6)
                + " "
                + matcher.group(7)
                + "'"
                + matcher.group(14);
    }

    private static String toSalesPolicyStatement(final Matcher matcher) {
        return "INSERT INTO BOOKING_PERFORMANCE_SALES_POLICIES (performance_id, order_opens_at, order_closes_at, max_hold_seat_count, hold_duration_seconds, version, created_at, created_by) VALUES ("
                + matcher.group(1)
                + ", '"
                + matcher.group(8)
                + " "
                + matcher.group(9)
                + "', '"
                + matcher.group(10)
                + " "
                + matcher.group(11)
                + "', "
                + matcher.group(12)
                + ", "
                + matcher.group(13)
                + ", 0"
                + matcher.group(14);
    }

    private record ShowSeed(
            int statementIndex,
            String statement,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime saleStartDate,
            LocalDateTime saleEndDate) {}

    private record PerformanceSeed(
            int statementIndex,
            String statement,
            int performanceNo,
            LocalDate startDate,
            LocalDateTime orderOpenTime,
            LocalDateTime orderCloseTime) {}

    private record ShowWindow(LocalDate endDate, LocalDateTime saleStartDate, LocalDateTime saleEndDate) {}
}
