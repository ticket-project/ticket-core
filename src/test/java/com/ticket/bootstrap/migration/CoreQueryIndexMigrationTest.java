package com.ticket.bootstrap.migration;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 11: {@code __root}(module-owned이 아닌 공용) migration만으로 실행되는 시나리오다. 실제 운영
 * 실행은 {@code spring.modulith.runtime.flyway-enabled=true}가 등록하는
 * {@code SpringModulithFlywayMigrationStrategy}가 {@code __root}와 각 module을 별도
 * {@code flyway_schema_history_*} table로 나눠 돌리므로, 이 테스트도
 * {@link ModulithFlywayTestSupport}로 그 실제 mechanism을 그대로 사용한다(module identifier 없이
 * 호출 = root만 돈다). booking module 자체 migration(FK 제거, outbox drop)은
 * {@link BookingModuleMigrationTest}가 다룬다.
 */
class CoreQueryIndexMigrationTest {

    @Test
    void applies_query_indexes_after_existing_schema_baseline() throws Exception {
        String url = databaseUrl("success");
        createExistingSchema(url, false);

        ModulithFlywayTestSupport.migrateRootOnly(url);

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(indexNames(connection, "PERFORMANCE_SEATS"))
                    .contains("UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT");
            assertThat(indexNames(connection, "ORDER_SEATS"))
                    .contains("IDX_ORDER_SEATS_ORDER_ID");
            // root 혼자서는 V5/V6이 만드는 custom outbox table을 만들기만 한다 — 제거하는 V2는
            // booking module 소유(BookingModuleMigrationTest 참고)라 여기서는 돌지 않는다.
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "ORDER_HOLD_RELEASE_OUTBOX")).isTrue();
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "ORDER_HOLD_CREATION_OUTBOX")).isTrue();
            // V8(Task 11)이 만드는 Spring Modulith JPA event publication registry.
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "EVENT_PUBLICATION")).isTrue();
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "EVENT_PUBLICATION_ARCHIVE")).isTrue();
        }
    }

    @Test
    void stops_migration_when_performance_seats_have_duplicates() throws Exception {
        String url = databaseUrl("duplicates");
        createExistingSchema(url, true);

        assertThatThrownBy(() -> ModulithFlywayTestSupport.migrateRootOnly(url))
                .isInstanceOf(FlywayException.class);
    }

    private void createExistingSchema(final String url, final boolean withDuplicates) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE performance_seats (performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
            if (withDuplicates) {
                statement.execute("INSERT INTO performance_seats VALUES (1, 10), (1, 10)");
            }
        }
    }

    private Set<String> indexNames(final Connection connection, final String tableName) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null) {
                    names.add(indexName);
                }
            }
        }
        return names;
    }

    private String databaseUrl(final String name) {
        return "jdbc:h2:mem:core-query-index-" + name
                + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }
}
