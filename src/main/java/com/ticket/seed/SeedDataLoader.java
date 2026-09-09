package com.ticket.seed;

import com.ticket.member.application.SeedLoadTestMembersUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

@Slf4j
@Component
@RequiredArgsConstructor
@Order(SeedDataLoader.ORDER)
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class SeedDataLoader implements ApplicationRunner {

    /**
     * 이 시드는 집합 기반이다. {@code seed/kopis-curated.sql}의 SHOW_GRADES·SHOW_SEATS·PERFORMANCE_SEATS는
     * {@code FROM SHOWS s CROSS JOIN SEATS st} 형태라 실행 시점에 존재하는 모든 행을 대상으로 삼는다.
     * 따라서 다른 시드가 먼저 SHOWS·SEATS에 행을 넣으면 그 행까지 휩쓸어 중복 등급을 만들고 실패한다.
     * 순서를 명시해 이 시드가 항상 가장 먼저 끝나도록 한다.
     */
    static final int ORDER = 0;

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_LOAD_TEST_MEMBER_COUNT = 100;
    private static final String SEED_SQL_RESOURCE = "seed/kopis-curated.sql";
    private static final String SEED_CHECK_SQL = "SELECT COUNT(*) FROM CATEGORIES";
    private static final String DEFAULT_LOAD_TEST_MEMBER_PASSWORD = "password1234";
    private static final String LOAD_TEST_MEMBER_EMAIL_PREFIX = "loadtest";
    private static final String LOAD_TEST_MEMBER_EMAIL_SUFFIX = "@test.com";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern SHOW_INSERT_PATTERN = Pattern.compile(
        "INSERT INTO SHOWS .*?VALUES \\((\\d+), .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2})', '([0-9]{4}-[0-9]{2}-[0-9]{2})', .*?, '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})',",
        Pattern.DOTALL
    );
    private static final Pattern PERFORMANCE_INSERT_PATTERN = Pattern.compile(
        "INSERT INTO PERFORMANCES .*?VALUES \\((\\d+), (\\d+), (\\d+), '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})', '([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9:]{8})',\\s*(NULL|\\d+),\\s*(\\d+)(.*)",
        Pattern.DOTALL
    );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SeedLoadTestMembersUseCase seedLoadTestMembersUseCase;

    @Value("${app.seed.batch-size:" + DEFAULT_BATCH_SIZE + "}")
    private int batchSize;

    @Value("${app.seed.load-test-members.enabled:true}")
    private boolean loadTestMembersEnabled = true;

    @Value("${app.seed.load-test-members.count:" + DEFAULT_LOAD_TEST_MEMBER_COUNT + "}")
    private int loadTestMemberCount = DEFAULT_LOAD_TEST_MEMBER_COUNT;

    @Value("${app.seed.load-test-members.password:" + DEFAULT_LOAD_TEST_MEMBER_PASSWORD + "}")
    private String loadTestMemberPassword = DEFAULT_LOAD_TEST_MEMBER_PASSWORD;

    @Override
    public void run(final ApplicationArguments args) {
        if (!shouldSeed()) {
            log.info("시드 적재를 건너뜁니다. CATEGORIES 테이블에 이미 데이터가 있습니다.");
            seedLoadTestMembers();
            return;
        }

        final List<String> statements = parseStatements();
        if (statements.isEmpty()) {
            log.info("시드 적재를 건너뜁니다. 실행할 SQL 문이 없습니다.");
            seedLoadTestMembers();
            return;
        }

        final List<String> executableStatements = splitPerformancePolicyStatements(statements);

        transactionTemplate.executeWithoutResult(status -> {
            executeInBatches(executableStatements);
            seedLoadTestMembers();
        });
        log.info("시드 적재를 완료했습니다. statements={}, batchSize={}", executableStatements.size(), batchSize);
    }

    void seedLoadTestMembers() {
        if (!loadTestMembersEnabled) {
            log.info("부하 테스트 회원 시드를 건너뜁니다. app.seed.load-test-members.enabled=false");
            return;
        }

        final int count = Math.max(0, loadTestMemberCount);
        if (count == 0) {
            log.info("부하 테스트 회원 시드를 건너뜁니다. count=0");
            return;
        }

        final SeedLoadTestMembersUseCase.Output output = seedLoadTestMembersUseCase.execute(
                new SeedLoadTestMembersUseCase.Input(
                        LOAD_TEST_MEMBER_EMAIL_PREFIX,
                        LOAD_TEST_MEMBER_EMAIL_SUFFIX,
                        count,
                        loadTestMemberPassword
                )
        );

        log.info("부하 테스트 회원 시드를 완료했습니다. requested={}, created={}", output.requested(), output.created());
    }

    private boolean shouldSeed() {
        try {
            final Integer count = jdbcTemplate.queryForObject(SEED_CHECK_SQL, Integer.class);
            return count == null || count == 0;
        } catch (Exception e) {
            log.warn("시드 사전 점검에 실패했지만 적재를 계속 진행합니다. 사유={}", e.getMessage());
            return true;
        }
    }

    private List<String> parseStatements() {
        final List<String> statements = new ArrayList<>();
        final StringBuilder current = new StringBuilder();

        for (String line : readSeedSqlLines()) {
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

        if (!current.isEmpty()) {
            statements.add(current.toString().trim());
        }

        return diversifyPerformanceDates(statements);
    }

    private List<String> readSeedSqlLines() {
        final ClassPathResource resource = new ClassPathResource(SEED_SQL_RESOURCE);
        final List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            throw new IllegalStateException("시드 SQL 리소스를 읽을 수 없습니다. resource=" + SEED_SQL_RESOURCE, e);
        }
    }

    private List<String> diversifyPerformanceDates(final List<String> statements) {
        final List<String> diversifiedStatements = new ArrayList<>(statements);
        final Map<Long, ShowSeed> shows = extractShows(statements);
        final Map<Long, List<PerformanceSeed>> performancesByShow = extractPerformances(statements);

        for (Map.Entry<Long, List<PerformanceSeed>> entry : performancesByShow.entrySet()) {
            final List<PerformanceSeed> performanceSeeds = entry.getValue().stream()
                .sorted(Comparator.comparingInt(PerformanceSeed::performanceNo))
                .toList();
            final ShowSeed showSeed = shows.get(entry.getKey());
            final ShowWindow showWindow = buildShowWindow(performanceSeeds, showSeed, diversifiedStatements);

            if (showSeed == null) {
                continue;
            }

            if (shouldRewriteShow(showSeed, showWindow)) {
                diversifiedStatements.set(showSeed.statementIndex(), rewriteShowStatement(showSeed.statement(), showWindow));
            }
        }

        return diversifiedStatements;
    }

    private ShowWindow buildShowWindow(
        final List<PerformanceSeed> performanceSeeds,
        final ShowSeed showSeed,
        final List<String> diversifiedStatements
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
            diversifiedStatements.set(performanceSeed.statementIndex(), rewritePerformanceStatement(performanceSeed.statement(), assignedDate));
            lastAssignedDate = assignedDate;
            earliestOrderOpenTime = earlierOf(earliestOrderOpenTime, performanceSeed.orderOpenTime());
            latestOrderCloseTime = laterOf(latestOrderCloseTime, assignedDate.atTime(performanceSeed.orderCloseTime().toLocalTime()));
        }

        return new ShowWindow(resolveEndDate(showSeed, lastAssignedDate), earliestOrderOpenTime, latestOrderCloseTime);
    }

    private Map<Long, ShowSeed> extractShows(final List<String> statements) {
        final Map<Long, ShowSeed> shows = new HashMap<>();

        for (int index = 0; index < statements.size(); index++) {
            final String statement = statements.get(index);
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

    private Map<Long, List<PerformanceSeed>> extractPerformances(final List<String> statements) {
        final Map<Long, List<PerformanceSeed>> performancesByShow = new HashMap<>();

        for (int index = 0; index < statements.size(); index++) {
            final String statement = statements.get(index);
            final Matcher matcher = PERFORMANCE_INSERT_PATTERN.matcher(statement);
            if (!matcher.find()) {
                continue;
            }

            final long showId = Long.parseLong(matcher.group(2));
            final int performanceNo = Integer.parseInt(matcher.group(3));
            final LocalDate startDate = LocalDate.parse(matcher.group(4));
            performancesByShow.computeIfAbsent(showId, ignored -> new ArrayList<>())
                .add(new PerformanceSeed(
                    index,
                    statement,
                    performanceNo,
                    startDate,
                    LocalDateTime.parse(matcher.group(8) + "T" + matcher.group(9)),
                    LocalDateTime.parse(matcher.group(10) + "T" + matcher.group(11))
                ));
        }

        return performancesByShow;
    }

    private boolean hasMultipleDates(final List<PerformanceSeed> performanceSeeds) {
        final LocalDate firstDate = performanceSeeds.get(0).startDate();
        return performanceSeeds.stream().anyMatch(performanceSeed -> !performanceSeed.startDate().equals(firstDate));
    }

    private boolean shouldRewriteShow(final ShowSeed showSeed, final ShowWindow showWindow) {
        return !showSeed.endDate().equals(showWindow.endDate())
            || !showSeed.saleStartDate().equals(showWindow.saleStartDate())
            || !showSeed.saleEndDate().equals(showWindow.saleEndDate());
    }

    private LocalDate resolveEndDate(final ShowSeed showSeed, final List<PerformanceSeed> performanceSeeds) {
        final LocalDate lastPerformanceDate = performanceSeeds.get(performanceSeeds.size() - 1).startDate();
        return resolveEndDate(showSeed, lastPerformanceDate);
    }

    private LocalDate resolveEndDate(final ShowSeed showSeed, final LocalDate performanceEndDate) {
        if (showSeed == null || performanceEndDate.isAfter(showSeed.endDate())) {
            return performanceEndDate;
        }
        return showSeed.endDate();
    }

    private LocalDateTime earliestOrderOpenTime(final List<PerformanceSeed> performanceSeeds) {
        LocalDateTime earliestOrderOpenTime = performanceSeeds.get(0).orderOpenTime();

        for (PerformanceSeed performanceSeed : performanceSeeds) {
            earliestOrderOpenTime = earlierOf(earliestOrderOpenTime, performanceSeed.orderOpenTime());
        }

        return earliestOrderOpenTime;
    }

    private LocalDateTime latestOrderCloseTime(final List<PerformanceSeed> performanceSeeds) {
        LocalDateTime latestOrderCloseTime = performanceSeeds.get(0).orderCloseTime();

        for (PerformanceSeed performanceSeed : performanceSeeds) {
            latestOrderCloseTime = laterOf(latestOrderCloseTime, performanceSeed.orderCloseTime());
        }

        return latestOrderCloseTime;
    }

    private LocalDateTime earlierOf(final LocalDateTime current, final LocalDateTime candidate) {
        if (candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }

    private LocalDateTime laterOf(final LocalDateTime current, final LocalDateTime candidate) {
        if (candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }

    private String rewriteShowStatement(final String statement, final ShowWindow showWindow) {
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

    private String rewritePerformanceStatement(final String statement, final LocalDate assignedDate) {
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

    /**
     * ADR 0006 "Performance의 책임 혼재" A2: {@code seed/kopis-curated.sql}의 {@code INSERT INTO
     * PERFORMANCES}는 여전히 (수정하지 않은 리소스 파일 안에서) 예매 정책 컬럼까지 함께 담고
     * 있다 — 대량 curated 데이터라 리소스 파일 자체를 고치는 대신, 이미 파싱된
     * {@link #PERFORMANCE_INSERT_PATTERN} group을 이용해 실행 직전에 PERFORMANCES(일정만)와
     * BOOKING_PERFORMANCE_SALES_POLICIES(정책) 두 INSERT로 분리한다. 날짜 다변화
     * ({@link #diversifyPerformanceDates})는 이 분리 이전, 기존 단일-statement 형태를 그대로 쓴다.
     */
    private List<String> splitPerformancePolicyStatements(final List<String> statements) {
        final List<String> result = new ArrayList<>(statements.size());
        for (String statement : statements) {
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

    private String toScheduleOnlyPerformanceStatement(final Matcher matcher) {
        return "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, created_at, created_by) VALUES ("
            + matcher.group(1) + ", "
            + matcher.group(2) + ", "
            + matcher.group(3) + ", '"
            + matcher.group(4) + " " + matcher.group(5) + "', '"
            + matcher.group(6) + " " + matcher.group(7) + "'"
            + matcher.group(14);
    }

    private String toSalesPolicyStatement(final Matcher matcher) {
        return "INSERT INTO BOOKING_PERFORMANCE_SALES_POLICIES (performance_id, order_opens_at, order_closes_at, max_hold_seat_count, hold_duration_seconds, version, created_at, created_by) VALUES ("
            + matcher.group(1) + ", '"
            + matcher.group(8) + " " + matcher.group(9) + "', '"
            + matcher.group(10) + " " + matcher.group(11) + "', "
            + matcher.group(12) + ", "
            + matcher.group(13) + ", 0"
            + matcher.group(14);
    }

    private void executeInBatches(final List<String> statements) {
        final int chunk = Math.max(1, batchSize);

        for (int i = 0; i < statements.size(); i += chunk) {
            final int end = Math.min(i + chunk, statements.size());
            final String[] sqlBatch = statements.subList(i, end).toArray(String[]::new);
            jdbcTemplate.batchUpdate(sqlBatch);
        }
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
