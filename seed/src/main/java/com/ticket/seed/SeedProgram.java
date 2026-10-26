package com.ticket.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
            System.err.println("[시드 실패] 설정을 읽을 수 없습니다.");
            System.err.println(indent(rootMessage(exception)));
            return 1;
        }

        System.out.println("== %s 시드 적재 ==".formatted(target.label()));
        System.out.println("  DB        : " + SeedConsole.maskedUrl(settings.jdbcUrl()));
        System.out.println("  계정      : " + settings.jdbcUsername());
        System.out.println("  시드 SQL  : " + settings.sqlPath());
        System.out.println("  테스트 회원 : %d명".formatted(Math.max(0, settings.loadTestMemberCount())));
        System.out.println("  부하 회차  : 2,000석 %d개, 15,000석 %d개"
                .formatted(
                        Math.max(0, settings.loadTestPerformanceCount()),
                        Math.max(0, settings.largeLoadTestPerformanceCount())));
        System.out.println("  배경 주문  : %,d건".formatted(Math.max(0, settings.backgroundOrderCount())));

        final DataSource dataSource =
                new DriverManagerDataSource(settings.jdbcUrl(), settings.jdbcUsername(), settings.jdbcPassword());

        try {
            SeedPreconditions.verify(dataSource, settings.jdbcUrl(), target);
        } catch (final RuntimeException exception) {
            System.err.println("[시드 실패] DB가 준비되지 않았습니다.");
            System.err.println(indent(rootMessage(exception)));
            return 1;
        }

        final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        final TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        System.out.println("");
        System.out.println("-- 작업별 결과 --");
        final List<String> committed = new ArrayList<>();
        for (final SeedTask task : tasks(jdbcTemplate, transactionTemplate, settings)) {
            final SeedTask.Outcome outcome;
            try {
                outcome = task.run();
            } catch (final RuntimeException exception) {
                System.out.println("  [실패] " + task.name());
                System.err.println("");
                System.err.println("[시드 실패] " + task.name());
                System.err.println(indent(rootMessage(exception)));
                System.err.println(
                        committed.isEmpty()
                                ? "  앞 작업 중 커밋된 것은 없습니다."
                                : "  이미 커밋돼 DB에 남아 있는 작업: " + String.join(", ", committed));
                // 대부분의 작업은 한 트랜잭션이라 실패하면 통째로 되돌아간다. 아래 두 작업만 예외다.
                System.err.println("  실패한 작업은 롤백됐습니다. 단, 배경 주문은 실패 전에 커밋한 회차가, ID 시퀀스 맞춤은 이미 반영한 시퀀스가 남습니다.");
                System.err.println("  뒤 작업은 실행하지 않았습니다.");
                return 1;
            }
            System.out.println("  [%s] %s".formatted(outcome.skipped() ? "건너뜀" : "적재", task.name()));
            System.out.println("      " + outcome.summary());
            if (!outcome.skipped()) {
                committed.add(task.name());
            }
        }

        System.out.println("");
        System.out.println("[시드 완료] 모든 작업이 정상 종료됐습니다.");
        return 0;
    }

    /**
     * 실행 순서가 곧 계약이다. 공용 시드가 먼저 GRADES에 VIP/R/S/A code를 만들고, 부하 테스트 픽스처가 그 code를 재사용한다 — 순서가 바뀌면 같은 code가 중복 생성돼 실패한다.
     * 회원은 공용 시드·부하 픽스처와 독립이지만 뒤에 둔다(가장 빠르게 다시 만들 수 있는 데이터다). 배경 주문은 공용 회차와 회원을 둘 다 읽으므로 그 뒤다. identity 시퀀스 동기화는 명시적 ID로
     * 넣는 적재가 모두 끝난 뒤여야 하므로 맨 마지막이다.
     */
    static List<SeedTask> tasks(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final SeedSettings settings) {
        return List.of(
                new CuratedSeedLoader(jdbcTemplate, transactionTemplate, settings.sqlPath()),
                new LoadTestFixtureSeeder(
                        jdbcTemplate,
                        transactionTemplate,
                        LoadTestFixtureSeeder.STANDARD,
                        settings.loadTestPerformanceCount()),
                new LoadTestFixtureSeeder(
                        jdbcTemplate,
                        transactionTemplate,
                        LoadTestFixtureSeeder.LARGE,
                        settings.largeLoadTestPerformanceCount()),
                new LoadTestMemberSeeder(jdbcTemplate, transactionTemplate, settings.loadTestMemberCount()),
                new BackgroundOrderSeeder(jdbcTemplate, transactionTemplate, settings.backgroundOrderCount()),
                new PostgreSqlIdentitySynchronizer(jdbcTemplate));
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
