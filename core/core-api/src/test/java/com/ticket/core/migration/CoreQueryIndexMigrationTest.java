package com.ticket.core.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreQueryIndexMigrationTest {

    @Test
    void applies_query_indexes_after_existing_schema_baseline() throws Exception {
        String url = databaseUrl("success");
        createExistingSchema(url, false);

        flyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(indexNames(connection, "PERFORMANCE_SEATS"))
                    .contains("UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT");
            assertThat(indexNames(connection, "ORDER_SEATS"))
                    .contains("IDX_ORDER_SEATS_ORDER_ID");
        }
    }

    @Test
    void stops_migration_when_performance_seats_have_duplicates() throws Exception {
        String url = databaseUrl("duplicates");
        createExistingSchema(url, true);

        assertThatThrownBy(() -> flyway(url).migrate())
                .isInstanceOf(FlywayException.class);
    }

    private Flyway flyway(final String url) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(
                        "classpath:db/migration",
                        "classpath:db/migration-vendor/h2"
                )
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    private void createExistingSchema(final String url, final boolean withDuplicates) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
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
