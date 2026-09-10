package com.ticket.seed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Driver;
import java.util.List;

/**
 * 로컬 H2 DB에 초기 데이터를 적재하고 종료하는 독립 실행 프로그램이다.
 *
 * <pre>
 *   .\gradlew.bat seedLocal
 * </pre>
 *
 * <p>{@code TicketApplication}을 띄우지 않는다. 웹 서버·Redis·OAuth 설정 없이 DB 접속과 적재에
 * 필요한 것만 쓴다. 실행 순서와 사전 조건은 {@code seed/README.md}가 원본이다.
 *
 * <p>성공하면 작업별 결과를 출력하고 종료 코드 0으로 끝난다. 실패하면 원인을 요약하고 0이 아닌
 * 종료 코드로 끝난다. 접속 비밀번호는 어떤 경로로도 출력하지 않는다.
 */
public final class SeedLocalMain {

    private SeedLocalMain() {
    }

    public static void main(final String[] args) {
        System.exit(execute());
    }

    /** 테스트가 실제 실행 경로를 그대로 밟을 수 있도록 package 범위로 둔다. */
    static int execute() {
        final SeedSettings settings;
        try {
            settings = SeedSettings.load();
        } catch (final RuntimeException exception) {
            SeedConsole.error("[시드 실패] 설정을 읽을 수 없습니다.");
            SeedConsole.error(indent(exception.getMessage()));
            return 1;
        }

        SeedConsole.info("== 로컬 시드 적재 ==");
        SeedConsole.info("  DB       : " + SeedConsole.maskedUrl(settings.jdbcUrl()));
        SeedConsole.info("  시드 SQL : " + settings.sqlPath());

        final DataSource dataSource = createDataSource(settings);
        try {
            SeedPreconditions.verify(dataSource, settings.jdbcUrl());
        } catch (final RuntimeException exception) {
            SeedConsole.error("[시드 실패] DB가 준비되지 않았습니다.");
            SeedConsole.error(indent(exception.getMessage()));
            return 1;
        }

        final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        final TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        return report(SeedRunner.run(tasks(jdbcTemplate, transactionTemplate, settings)));
    }

    /**
     * 실행 순서가 곧 계약이다. 공용 시드가 먼저 GRADES에 VIP/R/S/A code를 만들고, 부하 테스트
     * 픽스처가 그 code를 재사용한다 — 순서가 바뀌면 같은 code가 중복 생성돼 실패한다. 회원은
     * 다른 두 작업과 독립이지만 마지막에 둔다(가장 빠르게 다시 만들 수 있는 데이터다).
     */
    static List<SeedTask> tasks(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final SeedSettings settings
    ) {
        return List.of(
                new CuratedSeedLoader(
                        jdbcTemplate, transactionTemplate, settings.sqlPath(), settings.batchSize()),
                new LoadTestFixtureSeeder(
                        jdbcTemplate, transactionTemplate, settings.loadTestPerformanceCount()),
                new LoadTestMemberSeeder(
                        jdbcTemplate, transactionTemplate,
                        settings.loadTestMemberCount(), settings.loadTestMemberPassword())
        );
    }

    private static int report(final List<SeedRunner.Report> reports) {
        SeedConsole.info("");
        SeedConsole.info("-- 작업별 결과 --");
        for (final SeedRunner.Report report : reports) {
            SeedConsole.info("  [%s] %s".formatted(report.status().label(), report.taskName()));
            SeedConsole.info("      " + report.summary());
        }

        final List<SeedRunner.Report> failures = reports.stream()
                .filter(report -> report.status() == SeedRunner.Status.FAILED)
                .toList();

        if (failures.isEmpty()) {
            SeedConsole.info("");
            SeedConsole.info("[시드 완료] 모든 작업이 정상 종료됐습니다.");
            return 0;
        }

        SeedConsole.error("");
        SeedConsole.error("[시드 실패] " + failures.getFirst().taskName());
        for (final SeedRunner.Report failure : failures) {
            SeedConsole.error(indent(rootMessage(failure.failure())));
        }
        SeedConsole.error("  위 '작업별 결과'에서 어디까지 커밋됐는지 확인하세요.");
        return 1;
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        final StringBuilder builder = new StringBuilder();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(System.lineSeparator()).append("원인: ");
                }
                builder.append(current.getMessage());
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return builder.isEmpty() ? failure.toString() : builder.toString();
    }

    private static String indent(final String message) {
        return message.lines().map(line -> "  " + line).reduce((a, b) -> a + System.lineSeparator() + b).orElse("");
    }

    /**
     * H2 드라이버를 직접 넘긴다. {@code DriverManagerDataSource}의 이름 기반 로딩과 달리 드라이버가
     * classpath에 없으면 여기서 곧바로 드러난다.
     */
    private static DataSource createDataSource(final SeedSettings settings) {
        try {
            final Driver driver = (Driver) Class.forName("org.h2.Driver")
                    .getDeclaredConstructor().newInstance();
            return new SimpleDriverDataSource(
                    driver, settings.jdbcUrl(), settings.jdbcUsername(), settings.jdbcPassword());
        } catch (final ReflectiveOperationException exception) {
            throw new SeedFailure("H2 JDBC 드라이버를 초기화할 수 없습니다.", exception);
        }
    }
}
