package com.ticket.seed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;

/**
 * 공용 KOPIS 시드({@code seed/sql/kopis-curated.sql})를 적재한다.
 *
 * <p>기존 {@code com.ticket.seed.SeedDataLoader}(애플리케이션 기동 시 실행되던
 * {@code ApplicationRunner})의 적재 책임을 그대로 옮긴 것이다. 파싱·날짜 다변화·회차/판매정책
 * 분리는 {@link CuratedSeedStatements}가, 이미 적재됐는지 판정은 {@link CuratedSeedInventory}가
 * 맡는다.
 *
 * <p>적재 전체를 하나의 트랜잭션으로 감싼다. 중간에 실패하면 전부 되돌려야 다음 실행이
 * 반쯤 적재된 상태를 만나지 않는다.
 */
final class CuratedSeedLoader implements SeedTask {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Path sqlPath;
    private final int batchSize;

    CuratedSeedLoader(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final Path sqlPath,
            final int batchSize
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.sqlPath = sqlPath;
        this.batchSize = batchSize;
    }

    @Override
    public String name() {
        return "공용 시드 적재 (kopis-curated.sql)";
    }

    @Override
    public Outcome run() {
        final CuratedSeedStatements statements = CuratedSeedStatements.from(sqlPath);
        final CuratedSeedInventory inventory = CuratedSeedInventory.inspect(jdbcTemplate, statements);

        switch (inventory.decision()) {
            case ALREADY_COMPLETE -> {
                return Outcome.skipped("공용 시드가 이미 완전히 적재돼 있습니다. 중복 적재하지 않습니다.");
            }
            case PARTIAL -> throw new SeedFailure("""
                    공용 시드가 일부만 적재된 상태입니다. 자동으로 지우거나 채워 넣지 않습니다.

                    %s
                    필요한 조치
                      1. local 프로파일(ddl-auto: create)로 서버를 재기동해 스키마를 다시 만든 뒤
                      2. seedLocal을 다시 실행하세요.
                    """.formatted(inventory.describe()));
            case LOAD -> {
                // 아래에서 적재한다.
            }
        }

        final List<String> executable = statements.executableStatements();
        if (executable.isEmpty()) {
            throw new SeedFailure("시드 SQL에서 실행할 문장을 찾지 못했습니다: " + sqlPath);
        }

        transactionTemplate.executeWithoutResult(status -> executeInBatches(executable));

        return Outcome.done("SQL 문 %d개를 적재했습니다(batchSize=%d)."
                .formatted(executable.size(), Math.max(1, batchSize)));
    }

    private void executeInBatches(final List<String> statements) {
        final int chunk = Math.max(1, batchSize);

        for (int index = 0; index < statements.size(); index += chunk) {
            final int end = Math.min(index + chunk, statements.size());
            jdbcTemplate.batchUpdate(statements.subList(index, end).toArray(String[]::new));
        }
    }
}
