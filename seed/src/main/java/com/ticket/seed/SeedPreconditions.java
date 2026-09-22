package com.ticket.seed;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.sql.DataSource;

/**
 * 적재를 시작하기 전에 DB와 스키마가 준비됐는지 확인한다.
 *
 * <p>준비되지 않은 DB에서 적재를 시작하면 실패 원인이 "테이블 없음" 수백 개로 흩어진다. 여기서 먼저 막고, 무엇이 없는지와 무엇을 해야 하는지를 한 번에 알려준다.
 *
 * <p><b>스키마를 만들지 않는다.</b> 테이블 생성·삭제·초기화는 이 프로그램의 범위가 아니다. 로컬은 애플리케이션이 만들고(local 프로파일의 {@code ddl-auto: create}), 운영은
 * Flyway migration이 이미 만들어 둔 것을 전제로 한다.
 *
 * <p><b>확인 범위는 실제로 접속한 스키마다.</b> Oracle에서 {@link DatabaseMetaData#getTables}는 접속 계정이 볼 수 있는 다른 스키마의 동명 테이블까지 돌려준다 — 그것을
 * "준비됐다"고 읽으면 INSERT는 엉뚱한 곳을 보거나 권한 오류로 실패한다. 그래서 Oracle에서는 {@code USER_TABLES} / {@code USER_TAB_COLUMNS}로 접속 계정 소유
 * 객체만 본다.
 */
final class SeedPreconditions {
    /** 시드가 실제로 INSERT하는 테이블 전체다. 하나라도 없으면 적재를 시작하지 않는다. */
    private static final List<String> REQUIRED_TABLES = List.of(
            "CATEGORIES",
            "GENRES",
            "PERFORMERS",
            "VENUES",
            "SHOWS",
            "SHOW_GENRES",
            "PERFORMANCES",
            "SEATS",
            "GRADES",
            "PERFORMANCE_GRADES",
            "PERFORMANCE_SEATS",
            "BOOKING_PERFORMANCE_SALES_POLICIES",
            "MEMBERS");

    /**
     * 회원 적재가 쓰는 {@code MEMBERS} 컬럼이다. 앱의 회원 매핑이 바뀌면 여기서 먼저 드러난다 — {@link LoadTestMemberSeeder}의 INSERT가 조용히 어긋나는 것보다
     * 낫다.
     */
    private static final List<String> REQUIRED_MEMBER_COLUMNS =
            List.of("EMAIL", "PASSWORD", "NAME", "ROLE", "CREATED_AT", "CREATED_BY", "DELETED_AT");

    private SeedPreconditions() {}

    static void verify(final DataSource dataSource, final String jdbcUrl) {
        verify(dataSource, jdbcUrl, SeedTarget.LOCAL);
    }

    static void verify(final DataSource dataSource, final String jdbcUrl, final SeedTarget target) {
        try (Connection connection = dataSource.getConnection()) {
            final boolean oracle = isOracle(jdbcUrl);
            verifyTables(connection, jdbcUrl, target, oracle);
            verifyMemberColumns(connection, oracle);
        } catch (final SQLException exception) {
            throw new SeedFailure(
                    """
                    DB에 접속할 수 없습니다. url=%s
                      원인: %s
                    %s
                    """.formatted(SeedConsole.maskedUrl(jdbcUrl), exception.getMessage(), connectionHint(target)),
                    exception);
        }
    }

    private static boolean isOracle(final String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:oracle:");
    }

    private static String connectionHint(final SeedTarget target) {
        if (target == SeedTarget.PROD) {
            return """
                      -> SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD와,
                         Wallet을 쓴다면 TNS_ADMIN이 서버 설정과 같은지 확인하세요.""";
        }
        return """
                  -> local 프로파일로 애플리케이션이 기동된 적이 있는지, H2 파일 DB 경로가 맞는지 확인하세요.""";
    }

    private static void verifyTables(
            final Connection connection, final String jdbcUrl, final SeedTarget target, final boolean oracle)
            throws SQLException {
        final Set<String> present = readTableNames(connection, oracle);
        final List<String> missing = new ArrayList<>();
        for (final String table : REQUIRED_TABLES) {
            if (!present.contains(table)) {
                missing.add(table);
            }
        }

        if (missing.isEmpty()) {
            return;
        }

        throw new SeedFailure("""
                시드에 필요한 테이블이 없습니다. url=%s
                  스키마: %s
                  없는 테이블: %s
                %s
                """.formatted(
                        SeedConsole.maskedUrl(jdbcUrl),
                        schemaLabel(connection, oracle),
                        String.join(", ", missing),
                        missingTableHint(target)));
    }

    private static String missingTableHint(final SeedTarget target) {
        if (target == SeedTarget.PROD) {
            return """
                      -> 테이블 생성은 이 명령의 범위가 아닙니다. seedProd는 이미 준비된 테이블에 데이터만 넣습니다.
                         애플리케이션 배포(Flyway migration)로 스키마가 먼저 만들어져야 하고, 접속 계정이
                         그 테이블의 소유자여야 합니다.""";
        }
        return """
                  -> 스키마는 애플리케이션이 만듭니다. local 프로파일로 서버를 먼저 기동해
                     스키마가 만들어진 뒤에 seedLocal을 실행하세요.""";
    }

    private static void verifyMemberColumns(final Connection connection, final boolean oracle) throws SQLException {
        final Set<String> present = readColumnNames(connection, "MEMBERS", oracle);

        final List<String> missing = new ArrayList<>();
        for (final String column : REQUIRED_MEMBER_COLUMNS) {
            if (!present.contains(column)) {
                missing.add(column);
            }
        }

        if (missing.isEmpty()) {
            return;
        }

        throw new SeedFailure("""
                MEMBERS 테이블에 회원 적재가 필요한 컬럼이 없습니다.
                  없는 컬럼: %s
                  있는 컬럼: %s
                  -> 앱의 회원 매핑이 바뀌었을 수 있습니다. LoadTestMemberSeeder의 INSERT를 함께 고치세요.
                """.formatted(String.join(", ", missing), String.join(", ", present)));
    }

    private static Set<String> readTableNames(final Connection connection, final boolean oracle) throws SQLException {
        if (oracle) {
            return queryNames(connection, "SELECT table_name FROM USER_TABLES");
        }
        final Set<String> names = new LinkedHashSet<>();
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (tables.next()) {
                names.add(tables.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static Set<String> readColumnNames(final Connection connection, final String table, final boolean oracle)
            throws SQLException {
        if (oracle) {
            return queryNames(
                    connection, "SELECT column_name FROM USER_TAB_COLUMNS WHERE table_name = '" + table + "'");
        }
        final Set<String> names = new LinkedHashSet<>();
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, null)) {
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static Set<String> queryNames(final Connection connection, final String sql) throws SQLException {
        final Set<String> names = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                names.add(rows.getString(1).toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    /** 어느 스키마를 보고 판정했는지 알려준다. 다른 스키마의 동명 테이블과 헷갈리는 것을 막는 정보다. */
    private static String schemaLabel(final Connection connection, final boolean oracle) {
        try {
            if (oracle) {
                return connection.getMetaData().getUserName();
            }
            final String schema = connection.getSchema();
            return schema == null ? "(알 수 없음)" : schema;
        } catch (final SQLException exception) {
            return "(알 수 없음)";
        }
    }
}
