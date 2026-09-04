package com.ticket.bootstrap.migration;

import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.catalog.internal.domain.seat.Seat;
import com.ticket.catalog.internal.domain.show.Performer;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.ShowGrade;
import com.ticket.catalog.internal.domain.show.ShowSeat;
import com.ticket.catalog.internal.domain.show.Venue;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ticket-domain-module-redesign Phase 1 / Task 1: 재설계 전 현재 Venue/Seat/ShowGrade/ShowSeat/
 * PerformanceSeat schema를 계약으로 캡처한다.
 *
 * <p>이 테스트는 {@code docs/superpowers/specs/2026-09-04-ticket-domain-module-redesign.md}가 지적하는
 * 두 가지 현재 사실을 실측으로 고정한다.
 *
 * <ul>
 *   <li><b>Seat에는 Venue 연관관계가 없다.</b> {@code SEATS} table에 {@code venue_id} 컬럼이 없고,
 *   {@code VENUES}는 {@code SHOWS.venue_id}로만 참조된다.</li>
 *   <li><b>PerformanceSeat는 아직 등급(PerformanceGrade)도 낙관적 락도 갖지 않는다.</b> 현재
 *   {@code price} 컬럼 하나만 갖고, {@code performance_id}/{@code seat_id}는 scalar column(모듈 간
 *   cross-module FK 없음)이다. Phase 3(Task 6)에서 이 schema가 바뀌면 이 테스트도 그때 함께 갱신해야
 *   한다 — 지금은 "바뀌기 전" 상태를 잠그는 것이 목적이다.</li>
 * </ul>
 *
 * <p>이 5개 테이블은 어떤 Flyway migration도 만들지 않는 pre-Flyway baseline이다
 * ({@code docs/operations.md} 참고, {@link CatalogCommonCodeSchemaTest}의 baseline 주석과 같은 사실).
 * 그래서 이 테스트는 Flyway를 거치지 않고 {@link CatalogCommonCodeSchemaTest}와 같은 기법으로 순수
 * Hibernate {@code hbm2ddl.auto=create}가 현재 entity 매핑으로부터 만드는 schema를 그대로 검사한다.
 */
class CurrentSeatVenueShowGradeSchemaTest {

    private static final String URL =
            "jdbc:h2:mem:current-seat-venue-show-grade-schema;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void createSchemaFromCurrentEntities() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", URL)
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .build();

        final Metadata metadata = new MetadataSources(registry)
                .addAnnotatedClass(Venue.class)
                .addAnnotatedClass(Performer.class)
                .addAnnotatedClass(Show.class)
                .addAnnotatedClass(ShowGrade.class)
                .addAnnotatedClass(ShowSeat.class)
                .addAnnotatedClass(Seat.class)
                .addAnnotatedClass(PerformanceSeat.class)
                .buildMetadata();

        // hbm2ddl.auto=create가 SessionFactory 생성 시 schema를 실제로 만든다.
        sessionFactory = metadata.buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void seat에는_venue_연관관계가_없다() throws SQLException {
        try (Connection connection = connect()) {
            assertThat(columnNames(connection, "SEATS")).doesNotContain("VENUE_ID");
            assertThat(columnNames(connection, "SEATS"))
                    .contains("SECTION", "ROW_NO", "SEAT_NO", "FLOOR", "X", "Y");
            // Venue는 SHOWS.venue_id로만 참조된다(Seat가 아니라 Show가 Venue를 안다).
            assertThat(columnNames(connection, "SHOWS")).contains("VENUE_ID");
            assertThat(importedKeyReferencedTables(connection, "SHOWS")).contains("VENUES");
            assertThat(importedKeyReferencedTables(connection, "SEATS")).doesNotContain("VENUES");
        }
    }

    @Test
    void show_grade가_show당_등급_가격을_소유하고_show_seat가_그것을_좌석에_배정한다() throws SQLException {
        try (Connection connection = connect()) {
            assertThat(columnNames(connection, "SHOW_GRADES"))
                    .contains("SHOW_ID", "GRADE_CODE", "GRADE_NAME", "PRICE", "SORT_ORDER");
            assertThat(importedKeyReferencedTables(connection, "SHOW_GRADES")).contains("SHOWS");

            assertThat(columnNames(connection, "SHOW_SEATS"))
                    .contains("SHOW_ID", "SEAT_ID", "SHOW_GRADE_ID");
            assertThat(importedKeyReferencedTables(connection, "SHOW_SEATS"))
                    .contains("SHOWS", "SEATS", "SHOW_GRADES");
        }
    }

    @Test
    void performance_seat는_아직_grade나_version이_없는_scalar_id_전용_모델이다() throws SQLException {
        try (Connection connection = connect()) {
            final Set<String> columns = columnNames(connection, "PERFORMANCE_SEATS");
            assertThat(columns).contains("PERFORMANCE_ID", "SEAT_ID", "STATE", "PRICE");
            // 재설계(Task 6) 전에는 아래 컬럼이 없다 — 있으면 이 baseline 테스트가 이미 낡은 것이다.
            assertThat(columns).doesNotContain("PERFORMANCE_GRADE_ID", "UNIT_PRICE", "VERSION");

            // booking이 소유한 PerformanceSeat는 catalog의 Performance/Seat를 cross-module JPA로
            // 참조하지 않는다(ADR 0003 §4) — FK가 전혀 없는 scalar column이다.
            assertThat(importedKeyReferencedTables(connection, "PERFORMANCE_SEATS")).isEmpty();

            assertThat(uniqueIndexColumnSets(connection, "PERFORMANCE_SEATS"))
                    .contains(Set.of("PERFORMANCE_ID", "SEAT_ID"));
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, "sa", "");
    }

    private Set<String> columnNames(final Connection connection, final String tableName) throws SQLException {
        final Set<String> names = new HashSet<>();
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME"));
            }
        }
        return names;
    }

    private Set<String> importedKeyReferencedTables(final Connection connection, final String tableName) throws SQLException {
        final Set<String> tables = new HashSet<>();
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, tableName)) {
            while (keys.next()) {
                tables.add(keys.getString("PKTABLE_NAME"));
            }
        }
        return tables;
    }

    private Set<Set<String>> uniqueIndexColumnSets(final Connection connection, final String tableName) throws SQLException {
        final java.util.Map<String, Set<String>> byIndexName = new java.util.HashMap<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, true, false)) {
            while (indexes.next()) {
                final String indexName = indexes.getString("INDEX_NAME");
                final String columnName = indexes.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }
                byIndexName.computeIfAbsent(indexName, ignored -> new HashSet<>()).add(columnName);
            }
        }
        return new HashSet<>(byIndexName.values());
    }
}
