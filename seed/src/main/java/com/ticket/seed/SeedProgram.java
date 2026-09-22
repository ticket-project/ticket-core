package com.ticket.seed;

import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code seedLocal}과 {@code seedProd}가 공유하는 실행 본체다. 두 명령의 차이는 <b>설정을 어디서 읽는가</b>와 <b>기본값</b>뿐이고, 적재 순서·트랜잭션 경계·완전성
 * 판정·보고 형식은 하나다.
 *
 * <p>{@code TicketApplication}을 띄우지 않는다. 웹 서버·Redis·OAuth 설정 없이 DB 접속과 적재에 필요한 것만 쓴다.
 *
 * <p>성공하면 작업별 결과를 출력하고 종료 코드 0으로 끝난다. 실패하면 원인을 요약하고 0이 아닌 종료 코드로 끝난다. <b>접속 비밀번호는 어떤 경로로도 출력하지 않는다</b> — URL 안에 섞인
 * 자격증명도 {@link SeedConsole#maskedUrl}로 가린다.
 */
final class SeedProgram {
    private SeedProgram() {}

    static int execute(final SeedTarget target, final Supplier<SeedSettings> settingsSupplier) {
        final SeedSettings settings;
        try {
            settings = settingsSupplier.get();
        } catch (final RuntimeException exception) {
            SeedConsole.error("[시드 실패] 설정을 읽을 수 없습니다.");
            SeedConsole.error(indent(rootMessage(exception)));
            return 1;
        }

        SeedConsole.info("== %s 시드 적재 ==".formatted(target.label()));
        SeedConsole.info("  DB        : " + SeedConsole.maskedUrl(settings.jdbcUrl()));
        SeedConsole.info("  계정      : " + settings.jdbcUsername());
        SeedConsole.info("  시드 SQL  : " + settings.sqlPath());
        SeedConsole.info("  테스트 회원 : %d명".formatted(Math.max(0, settings.loadTestMemberCount())));
        SeedConsole.info("  부하 회차  : %d개".formatted(Math.max(0, settings.loadTestPerformanceCount())));

        final DataSource dataSource;
        try {
            dataSource = SeedDataSources.create(settings);
        } catch (final RuntimeException exception) {
            SeedConsole.error("[시드 실패] DB 드라이버를 준비할 수 없습니다.");
            SeedConsole.error(indent(rootMessage(exception)));
            return 1;
        }

        try {
            SeedPreconditions.verify(dataSource, settings.jdbcUrl(), target);
        } catch (final RuntimeException exception) {
            SeedConsole.error("[시드 실패] DB가 준비되지 않았습니다.");
            SeedConsole.error(indent(rootMessage(exception)));
            return 1;
        }

        final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        final TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        return report(SeedRunner.run(tasks(jdbcTemplate, transactionTemplate, settings)));
    }

    /**
     * 실행 순서가 곧 계약이다. 공용 시드가 먼저 GRADES에 VIP/R/S/A code를 만들고, 부하 테스트 픽스처가 그 code를 재사용한다 — 순서가 바뀌면 같은 code가 중복 생성돼 실패한다.
     * 회원은 다른 두 작업과 독립이지만 마지막에 둔다(가장 빠르게 다시 만들 수 있는 데이터다).
     */
    static List<SeedTask> tasks(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final SeedSettings settings) {
        return List.of(
                new CuratedSeedLoader(jdbcTemplate, transactionTemplate, settings.sqlPath(), settings.batchSize()),
                new LoadTestFixtureSeeder(jdbcTemplate, transactionTemplate, settings.loadTestPerformanceCount()),
                new LoadTestMemberSeeder(
                        jdbcTemplate,
                        transactionTemplate,
                        settings.loadTestMemberCount(),
                        settings.loadTestMemberPassword()));
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

        final List<String> committed = reports.stream()
                .filter(report -> report.status() == SeedRunner.Status.LOADED)
                .map(SeedRunner.Report::taskName)
                .toList();
        if (committed.isEmpty()) {
            SeedConsole.error("  커밋된 작업은 없습니다. DB 상태는 실행 전과 같습니다.");
        } else {
            SeedConsole.error("  이미 커밋돼 DB에 남아 있는 작업: " + String.join(", ", committed));
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
        return message.lines()
                .map(line -> "  " + line)
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("");
    }
}
