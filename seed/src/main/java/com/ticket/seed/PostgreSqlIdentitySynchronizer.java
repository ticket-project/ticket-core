package com.ticket.seed;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/** 명시적 ID로 적재한 뒤 identity 충돌을 방지한다. 적재 중에는 앱의 쓰기를 중지한다. */
final class PostgreSqlIdentitySynchronizer implements SeedTask {
    private final JdbcTemplate jdbc;

    PostgreSqlIdentitySynchronizer(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "PostgreSQL identity 시퀀스 동기화";
    }

    @Override
    public Outcome run() {
        final List<IdentityColumn> columns = jdbc.query(
                """
                SELECT table_name, column_name,
                       pg_get_serial_sequence(quote_ident(table_schema) || '.' || quote_ident(table_name), column_name)
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND is_identity = 'YES'
                """, (row, index) -> new IdentityColumn(row.getString(1), row.getString(2), row.getString(3)));
        for (final IdentityColumn column : columns) {
            final String table = quote(column.table());
            final String id = quote(column.column());
            final Long maximum = jdbc.queryForObject("SELECT MAX(" + id + ") FROM " + table, Long.class);
            if (maximum != null) {
                // 이미 소비한 시퀀스를 뒤로 돌리지 않는다. 빈 테이블의 nextval은 그대로 둔다.
                jdbc.queryForObject(
                        "SELECT setval(CAST(? AS regclass), GREATEST(?, (SELECT last_value FROM " + column.sequence()
                                + ")), true)",
                        Long.class,
                        column.sequence(),
                        maximum);
            }
        }
        return Outcome.done("identity 컬럼 %d개의 다음 ID를 확인했습니다.".formatted(columns.size()));
    }

    private static String quote(final String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record IdentityColumn(String table, String column, String sequence) {}
}
