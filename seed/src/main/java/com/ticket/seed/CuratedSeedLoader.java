package com.ticket.seed;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공용 KOPIS 시드({@code seed/sql/kopis-curated.sql})를 적재한다.
 *
 * <p>기존 {@code com.ticket.seed.SeedDataLoader}(애플리케이션 기동 시 실행되던 {@code ApplicationRunner})의 적재 책임을 그대로 옮긴 것이다. 파싱·문장 순서
 * 검사는 {@link CuratedSeedStatements}가, 이미 적재됐는지 판정은 {@link CuratedSeedInventory}가 맡는다.
 *
 * <p>적재 전체를 하나의 트랜잭션으로 감싼다. 중간에 실패하면 전부 되돌려야 다음 실행이 반쯤 적재된 상태를 만나지 않는다. 같은 트랜잭션 안에서 {@link CuratedSeedVerifier}가 커밋 직전에
 * 관계를 검증한다 — 좌석이 없는 공연장·회차가 남은 채로 "정상 완료"가 되지 않게 한다.
 *
 * <p>이미 적재된 DB에 시드 SQL이 새로 수집한 행만큼 앞서 있으면 그 행만 더한다({@link #appendStatements}). 운영 공연을 갱신하는 경로다.
 */
final class CuratedSeedLoader implements SeedTask {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Path sqlPath;

    CuratedSeedLoader(
            final JdbcTemplate jdbcTemplate, final TransactionTemplate transactionTemplate, final Path sqlPath) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.sqlPath = sqlPath;
    }

    @Override
    public String name() {
        return "공용 시드 적재 (kopis-curated.sql)";
    }

    @Override
    public Outcome run() {
        final CuratedSeedStatements statements = CuratedSeedStatements.from(sqlPath);
        final CuratedSeedInventory inventory = CuratedSeedInventory.inspect(jdbcTemplate, statements);

        return switch (inventory.decision()) {
            case ALREADY_COMPLETE -> Outcome.skipped("공용 시드가 이미 완전히 적재돼 있습니다. 중복 적재하지 않습니다.");
            case LOAD -> load(statements.statements(), "SQL 문 %d개를 적재했습니다.");
            case PARTIAL -> load(appendStatements(statements, inventory), "새로 수집한 행을 더했습니다(SQL 문 %d개).");
        };
    }

    private Outcome load(final List<String> executable, final String message) {
        if (executable.isEmpty()) {
            throw new SeedFailure("시드 SQL에서 실행할 문장을 찾지 못했습니다: " + sqlPath);
        }

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.batchUpdate(executable.toArray(String[]::new));
            // 커밋 전에 관계를 확인한다. 누락된 상태가 "정상 적재"로 끝나면 안 된다.
            CuratedSeedVerifier.verify(jdbcTemplate);
        });

        return Outcome.done(message.formatted(executable.size()));
    }

    /**
     * 시드 SQL에 새로 수집한 행이 더해졌을 때(운영 DB 공연 갱신) 없는 리터럴 행과 뒤쪽 집합 기반 INSERT만 남긴다.
     *
     * <p>DB의 공용 행이 전부 파일에 있고 파일에만 있는 행이 있을 때만 더한다. 파일에 없는 행이 DB에 있거나(다른 데이터·옛 파일) 더할 행이 없으면(뒤쪽 테이블만 빈 손상) 예전처럼 실패한다. 기존
     * 행의 관계가 깨졌다면 커밋 전 {@link CuratedSeedVerifier}가 전체를 되돌린다.
     */
    private List<String> appendStatements(
            final CuratedSeedStatements statements, final CuratedSeedInventory inventory) {
        final Set<CuratedSeedStatements.LiteralKey> fileKeys = statements.literalKeys();
        final Set<CuratedSeedStatements.LiteralKey> existing = new HashSet<>();
        fileKeys.stream()
                .map(key -> key.table() + "." + key.column())
                .distinct()
                .forEach(tableColumn -> existing.addAll(existingKeys(tableColumn)));

        // SEATS 리터럴은 공연장 1의 좌석 템플릿뿐이고 나머지 공연장 좌석은 복제로 생기므로 비교에서 뺀다.
        final List<CuratedSeedStatements.LiteralKey> unknown = existing.stream()
                .filter(key -> !key.table().equals("SEATS") && !fileKeys.contains(key))
                .sorted(Comparator.comparing(CuratedSeedStatements.LiteralKey::table)
                        .thenComparingLong(CuratedSeedStatements.LiteralKey::value))
                .toList();
        final long missing =
                fileKeys.stream().filter(key -> !existing.contains(key)).count();
        if (!unknown.isEmpty() || missing == 0) {
            throw new SeedFailure("""
                    공용 시드가 일부만 적재된 상태입니다. 자동으로 지우거나 채워 넣지 않습니다.
                    %s
                    %s
                    필요한 조치
                      1. 접속 대상과 일부만 적재된 원인을 확인하세요. 운영 데이터는 자동으로 삭제하지 않습니다.
                      2. 폐기 가능한 로컬 DB라면 새 DB에 migration을 적용한 뒤 seedLocal을 다시 실행하세요.
                    """.formatted(
                            unknown.isEmpty()
                                    ? "  시드 SQL에 DB에 없는 새 행이 없습니다."
                                    : "  시드 SQL에 없는 행이 DB에 %d개 있습니다(첫 행: %s). 새 행만 더할 수 없습니다."
                                            .formatted(unknown.size(), unknown.getFirst()),
                            inventory.describe()));
        }

        return statements.statements().stream()
                .filter(statement -> statements
                        .literalKey(statement)
                        .map(key -> !existing.contains(key))
                        .orElse(true))
                .toList();
    }

    private List<CuratedSeedStatements.LiteralKey> existingKeys(final String tableColumn) {
        final String table = tableColumn.substring(0, tableColumn.indexOf('.'));
        final String column = tableColumn.substring(tableColumn.indexOf('.') + 1);
        return jdbcTemplate
                .queryForList(
                        "SELECT " + column + " FROM " + table + " WHERE " + column + " < "
                                + LoadTestFixtureSeeder.ID_BASE,
                        Long.class)
                .stream()
                .map(value -> new CuratedSeedStatements.LiteralKey(table, column, value))
                .toList();
    }
}
