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
 * Task 11 Step 3/4: {@code booking} module 소유 migration을 {@code __root}와 함께
 * {@code SpringModulithFlywayMigrationStrategy}(module-aware Flyway)로 실행했을 때의 결과를
 * 검증한다.
 *
 * <ul>
 *   <li>{@code db/migration-vendor/h2/booking/V1__...}: Task 7 이전 {@code PerformanceSeat}의
 *   {@code @ManyToOne} 매핑이 남겼을 수 있는 실제 FK CONSTRAINT를 이름과 무관하게 제거한다(없으면
 *   no-op).</li>
 *   <li>{@code db/migration/booking/V2__...}: Task 8이 대체한 custom hold outbox table을
 *   제거한다.</li>
 * </ul>
 *
 * <p>booking은 root와 별도의 {@code flyway_schema_history_booking} table로 추적되므로, 버전
 * 번호(V1, V2)가 root의 기존 이력(V2..V8)과 겹쳐도 충돌하지 않는다 — 이 자체가
 * module-aware Flyway의 핵심이다.
 */
class BookingModuleMigrationTest {

    @Test
    void drops_legacy_fk_and_outbox_tables_when_present() throws Exception {
        final String url = databaseUrl("present");
        createSchemaWithLegacyFkAndOutboxDependencies(url);

        ModulithFlywayTestSupport.migrate(url, List.of("booking"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(importedKeyTables(connection, "PERFORMANCE_SEATS")).isEmpty();
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "ORDER_HOLD_RELEASE_OUTBOX")).isFalse();
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "ORDER_HOLD_CREATION_OUTBOX")).isFalse();
            // root와 booking이 각자 독립된 schema history table을 쓴다(대소문자 무관하게 확인).
            assertThat(schemaHistoryTableNames(connection))
                    .anyMatch(name -> name.equalsIgnoreCase("flyway_schema_history"))
                    .anyMatch(name -> name.equalsIgnoreCase("flyway_schema_history_booking"));
        }
    }

    @Test
    void no_op_when_legacy_fk_already_absent() throws Exception {
        final String url = databaseUrl("absent");
        createSchemaWithoutLegacyFk(url);

        // FK가 전혀 없어도 예외 없이 끝나야 한다.
        ModulithFlywayTestSupport.migrate(url, List.of("booking"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "PERFORMANCE_SEATS")).isTrue();
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "ORDER_HOLD_RELEASE_OUTBOX")).isFalse();
        }
    }

    private void createSchemaWithLegacyFkAndOutboxDependencies(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE seats (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (" +
                    "  id BIGINT PRIMARY KEY, " +
                    "  performance_id BIGINT NOT NULL, " +
                    "  seat_id BIGINT NOT NULL, " +
                    "  CONSTRAINT some_arbitrary_legacy_fk_name FOREIGN KEY (performance_id) REFERENCES performances, " +
                    "  CONSTRAINT another_arbitrary_legacy_fk_name FOREIGN KEY (seat_id) REFERENCES seats" +
                    ")");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
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

    private void createSchemaWithoutLegacyFk(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (id BIGINT PRIMARY KEY, performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
        }
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
        return "jdbc:h2:mem:booking-module-migration-" + name
                + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }
}
