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
 * BC(Bounded Context) 재편 전에는 이 클래스가 {@code show} module의 {@link com.ticket.venue.seat.domain.Seat}/
 * {@link com.ticket.venue.facility.domain.Venue} Hibernate 매핑을 검증했다(옛 catalog V3). 물리 공연장·좌석이
 * venue module로 분리되며 그 검증은 {@code VenueModuleSlicingSchemaTest}로 옮겨갔고, 이 클래스는
 * show가 그 뒤에도 계속 소유하는 {@code SHOWS.venue_id} scalar 컬럼(옛 {@code @ManyToOne Venue venue}
 * 매핑이 남긴 컬럼)에 대해 재목적화됐다.
 *
 * <p>{@code show}의 {@code Show} entity는 {@code venue_id}를 값으로만 갖고(ADR 0003 §4, 모듈을
 * 넘나드는 JPA 연관관계 금지) DB의 FK CONSTRAINT는 만들지 않는다. 하지만 옛 매핑(Hibernate
 * ddl-auto=create가 {@code @ManyToOne Venue venue}로 만든 FK)이 남아있는 환경이 있을 수 있어, show의
 * V8이 {@code LikeModuleMigrationTest}와 같은 방식(이름과 무관하게 컬럼 기준으로 동적 조회)으로
 * 그 FK를 제거한다. 이 테스트는 그 두 경로(FK가 남아있는 환경/이미 없는 환경) 모두 예외 없이 끝나고
 * 실제로 FK가 사라지는지 검증한다.
 */
class ShowModuleSlicingSchemaTest {

    @Test
    void drops_legacy_venue_fk_when_present() throws Exception {
        final String url = databaseUrl("present");
        createSchemaWithLegacyVenueFk(url);

        ModulithFlywayTestSupport.migrate(url, List.of("show"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "SHOWS")).isTrue();
            assertThat(columnExists(connection, "SHOWS", "VENUE_ID")).isTrue();
            assertThat(importedKeyTables(connection, "SHOWS")).isEmpty();
        }
    }

    @Test
    void no_op_when_legacy_venue_fk_already_absent() throws Exception {
        final String url = databaseUrl("absent");
        createSchemaWithoutLegacyVenueFk(url);

        // FK가 전혀 없어도 예외 없이 끝나야 한다.
        ModulithFlywayTestSupport.migrate(url, List.of("show"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(columnExists(connection, "SHOWS", "VENUE_ID")).isTrue();
            assertThat(importedKeyTables(connection, "SHOWS")).isEmpty();
        }
    }

    /**
     * PERFORMANCES/PERFORMANCE_SEATS/ORDER_SEATS/VENUES는 어떤 Flyway migration도 만들지 않는
     * pre-Flyway baseline이고, {@code __root}의 migration(다른 어떤 module을 지정해도 항상 함께
     * 실행된다)이 참조하므로 최소 baseline으로 필요하다({@code LikeModuleMigrationTest}의
     * baseline과 같은 이유). SHOWS는 이 테스트가 검증하는 대상이라 venue_id + 옛 FK를 직접 만든다.
     */
    private void createSchemaWithLegacyVenueFk(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE venues (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE shows (" +
                    "  id BIGINT PRIMARY KEY, " +
                    "  venue_id BIGINT, " +
                    "  CONSTRAINT some_arbitrary_legacy_venue_fk FOREIGN KEY (venue_id) REFERENCES venues" +
                    ")");
        }
    }

    private void createSchemaWithoutLegacyVenueFk(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE shows (id BIGINT PRIMARY KEY, venue_id BIGINT)");
        }
    }

    private boolean columnExists(final Connection connection, final String tableName, final String columnName)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return columns.next();
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
        return "jdbc:h2:mem:show-module-slicing-schema-" + name
                + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }
}
