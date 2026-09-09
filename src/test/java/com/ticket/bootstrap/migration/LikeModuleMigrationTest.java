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
 * {@code like} module 소유 migration을 {@code SpringModulithFlywayMigrationStrategy}
 * (module-aware Flyway)로 실행했을 때의 결과를 검증한다.
 *
 * <ul>
 *   <li>{@code db/migration-vendor/{h2,oracle}/like/V1__...}(옛 catalog V1): {@code ShowLike.member}가
 *   {@code @ManyToOne}에서 scalar {@code memberId}로 바뀌기 전 남았을 수 있는 FK를 이름과 무관하게
 *   제거한다(없으면 no-op).</li>
 *   <li>{@code .../like/V2__...}: {@code ShowLike.show}가 scalar {@code showId}로 바뀌며 남았을 수
 *   있는 FK를 같은 방식으로 제거한다.</li>
 *   <li>{@code .../like/V3__...}: {@code SHOW_LIKES}를 {@code LIKES}로 일반화한다(대상 종류
 *   {@code like_type} 도입, {@code show_id} -> {@code target_id}). {@code ddl-auto=create}
 *   환경에서 Hibernate가 이미 {@code LIKES}를 만들어 뒀다면 no-op이어야 한다.</li>
 * </ul>
 *
 * <p>like는 catalog(현 show)가 소유했던 옛 이력({@code flyway_schema_history_catalog})과
 * 무관하게 새 {@code flyway_schema_history_like} table로 처음부터 적용된다 — 그래서 모든
 * migration이 이미 적용된 스키마에서도 예외 없이 끝나야 한다.
 */
class LikeModuleMigrationTest {

    @Test
    void drops_legacy_member_and_show_fk_when_present() throws Exception {
        final String url = databaseUrl("present");
        createSchemaWithLegacyFks(url);

        ModulithFlywayTestSupport.migrate(url, List.of("like"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(importedKeyTables(connection, "LIKES")).isEmpty();
            assertThat(schemaHistoryTableNames(connection))
                    .anyMatch(name -> name.equalsIgnoreCase("flyway_schema_history_like"));
        }
    }

    @Test
    void no_op_when_legacy_fk_already_absent() throws Exception {
        final String url = databaseUrl("absent");
        createSchemaWithoutLegacyFks(url);

        // FK가 전혀 없어도 예외 없이 끝나야 한다.
        ModulithFlywayTestSupport.migrate(url, List.of("like"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "LIKES")).isTrue();
            assertThat(importedKeyTables(connection, "LIKES")).isEmpty();
        }
    }

    @Test
    void generalizes_show_likes_to_likes_when_legacy_table_present() throws Exception {
        final String url = databaseUrl("generalize");
        createLegacyShowLikesSchema(url);

        ModulithFlywayTestSupport.migrate(url, List.of("like"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "LIKES")).isTrue();
            try (ResultSet rows = statement.executeQuery(
                    "SELECT LIKE_TYPE, TARGET_ID FROM LIKES ORDER BY ID")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("LIKE_TYPE")).isEqualTo("SHOW");
                assertThat(rows.getLong("TARGET_ID")).isEqualTo(2L);
            }
        }
    }

    @Test
    void no_op_when_likes_table_already_generalized() throws Exception {
        final String url = databaseUrl("already-generalized");
        createGeneralizedLikesSchema(url);

        // Hibernate ddl-auto=create가 이미 LIKES를 만들어 둔 환경을 흉내낸다 — no-op이어야 한다.
        ModulithFlywayTestSupport.migrate(url, List.of("like"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "LIKES")).isTrue();
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

    private void createLegacyShowLikesSchema(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
            statement.execute(
                    "CREATE TABLE show_likes (" +
                    "  id BIGINT PRIMARY KEY, " +
                    "  member_id BIGINT NOT NULL, " +
                    "  show_id BIGINT NOT NULL, " +
                    "  CONSTRAINT UK_SHOW_LIKES_MEMBER_SHOW UNIQUE (member_id, show_id)" +
                    ")");
            statement.execute("INSERT INTO show_likes (id, member_id, show_id) VALUES (1, 1, 2)");
        }
    }

    private void createGeneralizedLikesSchema(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
            statement.execute(
                    "CREATE TABLE likes (" +
                    "  id BIGINT PRIMARY KEY, " +
                    "  member_id BIGINT NOT NULL, " +
                    "  like_type VARCHAR(20) NOT NULL, " +
                    "  target_id BIGINT NOT NULL, " +
                    "  CONSTRAINT UK_LIKES_MEMBER_TARGET UNIQUE (member_id, like_type, target_id)" +
                    ")");
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
        return "jdbc:h2:mem:like-module-migration-" + name
                + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }
}
