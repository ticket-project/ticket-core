package com.ticket.seed;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 적재를 시작하기 전에 DB와 스키마가 준비됐는지 확인한다.
 *
 * <p>준비되지 않은 DB에서 적재를 시작하면 실패 원인이 "테이블 없음" 수백 개로 흩어진다. 여기서
 * 먼저 막고, 무엇이 없는지와 무엇을 해야 하는지를 한 번에 알려준다.
 *
 * <p><b>스키마를 만들지 않는다.</b> 테이블 생성·삭제·초기화는 애플리케이션의 몫이다
 * (local 프로파일의 {@code ddl-auto: create}).
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
            "MEMBERS"
    );

    /**
     * 회원 적재가 쓰는 {@code MEMBERS} 컬럼이다. 앱의 회원 매핑이 바뀌면 여기서 먼저 드러난다 —
     * {@link LoadTestMemberSeeder}의 INSERT가 조용히 어긋나는 것보다 낫다.
     */
    private static final List<String> REQUIRED_MEMBER_COLUMNS = List.of(
            "EMAIL", "PASSWORD", "NAME", "ROLE", "CREATED_AT", "CREATED_BY", "DELETED_AT"
    );

    private SeedPreconditions() {
    }

    static void verify(final DataSource dataSource, final String jdbcUrl) {
        try (Connection connection = dataSource.getConnection()) {
            final DatabaseMetaData metaData = connection.getMetaData();
            verifyTables(metaData, jdbcUrl);
            verifyMemberColumns(metaData);
        } catch (final SQLException exception) {
            throw new SeedFailure("""
                    DB에 접속할 수 없습니다. url=%s
                      원인: %s
                      -> local 프로파일로 애플리케이션이 기동된 적이 있는지, H2 파일 DB 경로가 맞는지 확인하세요.
                    """.formatted(SeedConsole.maskedUrl(jdbcUrl), exception.getMessage()), exception);
        }
    }

    private static void verifyTables(final DatabaseMetaData metaData, final String jdbcUrl) throws SQLException {
        final Set<String> present = readTableNames(metaData);
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
                  없는 테이블: %s
                  -> 스키마는 애플리케이션이 만듭니다. local 프로파일로 서버를 먼저 기동해
                     스키마가 만들어진 뒤에 seedLocal을 실행하세요.
                """.formatted(SeedConsole.maskedUrl(jdbcUrl), String.join(", ", missing)));
    }

    private static void verifyMemberColumns(final DatabaseMetaData metaData) throws SQLException {
        final Set<String> present = new LinkedHashSet<>();
        try (ResultSet columns = metaData.getColumns(null, null, "MEMBERS", null)) {
            while (columns.next()) {
                present.add(columns.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
            }
        }

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

    private static Set<String> readTableNames(final DatabaseMetaData metaData) throws SQLException {
        final Set<String> names = new LinkedHashSet<>();
        try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                names.add(tables.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }
}
