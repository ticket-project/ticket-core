package com.ticket.bootstrap.migration;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code favorite} module 소유 migration을 {@code SpringModulithFlywayMigrationStrategy}
 * (module-aware Flyway)로 실행했을 때의 결과를 검증한다.
 *
 * <ul>
 *   <li>{@code db/migration-vendor/{h2,oracle}/favorite/V1__...}(옛 catalog V1): {@code ShowLike.member}가
 *   {@code @ManyToOne}에서 scalar {@code memberId}로 바뀌기 전 남았을 수 있는 FK를 이름과 무관하게
 *   제거한다(없으면 no-op).</li>
 *   <li>{@code .../favorite/V2__...}: 이번에 {@code ShowLike.show}가 scalar {@code showId}로 바뀌며
 *   남았을 수 있는 FK를 같은 방식으로 제거한다.</li>
 * </ul>
 *
 * <p>favorite는 catalog(현 show)가 소유했던 옛 이력({@code flyway_schema_history_catalog})과
 * 무관하게 새 {@code flyway_schema_history_favorite} table로 처음부터 적용된다 — 그래서 두
 * migration 모두 이미 적용된 스키마(FK가 이미 없는 경우)에서도 예외 없이 끝나야 한다.
 */
class FavoriteModuleMigrationTest {

    @Test
    void drops_legacy_member_and_show_fk_when_present() throws Exception {
        final String url = databaseUrl("present");
        createSchemaWithLegacyFks(url);

        ModulithFlywayTestSupport.migrate(url, List.of("favorite"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(importedKeyTables(connection, "SHOW_LIKES")).isEmpty();
            assertThat(schemaHistoryTableNames(connection))
                    .anyMatch(name -> name.equalsIgnoreCase("flyway_schema_history_favorite"));
        }
    }

    @Test
    void no_op_when_legacy_fk_already_absent() throws Exception {
        final String url = databaseUrl("absent");
        createSchemaWithoutLegacyFks(url);

        // FK가 전혀 없어도 예외 없이 끝나야 한다.
        ModulithFlywayTestSupport.migrate(url, List.of("favorite"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "SHOW_LIKES")).isTrue();
            assertThat(importedKeyTables(connection, "SHOW_LIKES")).isEmpty();
        }
    }

    private void createSchemaWithLegacyFks(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            // __root의 V2(performance_queue_policies FK)~V4(order_seats 인덱스)는 다른 module의
            // schema에도 걸쳐 있으므로, 이 module만 적용하는 테스트에서도 최소 baseline으로 있어야
            // 한다(BookingModuleSlicingSchemaTest의 legacy baseline과 같은 이유).
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE members (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE shows (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE show_likes (" +
                    "  id BIGINT PRIMARY KEY, " +
                    "  member_id BIGINT NOT NULL, " +
                    "  show_id BIGINT NOT NULL, " +
                    "  CONSTRAINT some_arbitrary_legacy_member_fk FOREIGN KEY (member_id) REFERENCES members, " +
                    "  CONSTRAINT some_arbitrary_legacy_show_fk FOREIGN KEY (show_id) REFERENCES shows" +
                    ")");
        }
    }

    private void createSchemaWithoutLegacyFks(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
            statement.execute(
                    "CREATE TABLE show_likes (id BIGINT PRIMARY KEY, member_id BIGINT NOT NULL, show_id BIGINT NOT NULL)");
        }
    }

    private Set<String> schemaHistoryTableNames(final Connection connection) throws SQLException {
        final Set<String> names = new HashSet<>();
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                names.add(tables.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private Set<String> importedKeyTables(final Connection connection, final String tableName) throws SQLException {
        final Set<String> names = new HashSet<>();
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, tableName)) {
            while (keys.next()) {
                names.add(keys.getString("FK_NAME"));
            }
        }
        return names;
    }

    private String databaseUrl(final String name) {
        return "jdbc:h2:mem:favorite-module-migration-" + name
                + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }
}
