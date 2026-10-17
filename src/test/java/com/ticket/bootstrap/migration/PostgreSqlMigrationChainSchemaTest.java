package com.ticket.bootstrap.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ticket.testsupport.PostgreSqlTestDatabase;
import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * 로컬과 RDS가 사용하는 migration을 실제 PostgreSQL에 적용하고 매핑·제약·긴 문자열을 검증한다.
 *
 * <p>스키마의 원본은 migration이다. entity만 바꾸고 migration을 빠뜨리면 운영의 {@code validate}에서야 기동이 실패한다. 여기서는 빈 schema에 모든 module
 * migration을 운영과 같은 순서로 적용한 뒤 Hibernate {@code validate}로 context를 띄운다. {@code validate}는 테이블·컬럼·타입만 보므로 인덱스와 제약은 따로
 * 확인한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class PostgreSqlMigrationChainSchemaTest {
    private static String url;

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        url = PostgreSqlTestDatabase.newSchemaUrl("migration_chain");
        ModulithFlywayTestSupport.applyMigrations(url, MigratedSchema.MODULES_IN_RUNTIME_ORDER);
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(url, PostgreSqlTestDatabase.username(), PostgreSqlTestDatabase.password()));
    }

    @Test
    void 스키마가_entity와_일치하고_migration을_재실행할_수_있다() {
        assertThatCode(() -> validate(url)).doesNotThrowAnyException();
        assertThatCode(() -> ModulithFlywayTestSupport.applyMigrations(url, MigratedSchema.MODULES_IN_RUNTIME_ORDER))
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name IN ('order_hold_release_outbox', 'order_hold_creation_outbox', 'performance_queue_policies')",
                        Integer.class))
                .isZero();
    }

    @Test
    void 긴_문자열과_이벤트_payload를_LOB_API_없이_저장하고_조회한다() {
        String info = "공연 상세 설명".repeat(1000);
        Long showId = jdbc.queryForObject(
                "INSERT INTO shows (venue_id, view_count, info, created_at, created_by) VALUES (1, 0, ?, CURRENT_TIMESTAMP, 'PG_TEST') RETURNING id",
                Long.class,
                info);
        assertThat(jdbc.queryForObject("SELECT info FROM shows WHERE id = ?", String.class, showId))
                .isEqualTo(info);
        for (String table : List.of("event_publication", "event_publication_archive")) {
            UUID id = UUID.randomUUID();
            String payload = "x".repeat(3000);
            jdbc.update(
                    "INSERT INTO " + table
                            + " (id, publication_date, listener_id, serialized_event, event_type, completion_attempts, status) VALUES (?, CURRENT_TIMESTAMP, 'listener', ?, 'com.ticket.booking.OrderTerminated', 0, 'PUBLISHED')",
                    id,
                    payload);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM " + table + " WHERE id = ? AND serialized_event = ?",
                            Integer.class,
                            id,
                            payload))
                    .isEqualTo(1);
        }
    }

    @Test
    void 모듈간_외래키_없이_각_모듈을_독립적으로_migration한다() {
        for (String module : List.of("member", "venue", "show", "booking", "like", "payment")) {
            String moduleUrl = PostgreSqlTestDatabase.newSchemaUrl("slice_" + module);
            assertThatCode(() -> ModulithFlywayTestSupport.applyMigrations(moduleUrl, List.of(module)))
                    .doesNotThrowAnyException();
        }
        assertThat(jdbc.queryForList("""
                SELECT c.relname FROM pg_index i JOIN pg_class t ON t.oid = i.indrelid
                JOIN pg_class c ON c.oid = i.indexrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = current_schema() AND NOT i.indisunique
                  AND t.relname NOT LIKE 'flyway_schema_history%'
                """, String.class))
                .containsExactlyInAnyOrder("idx_orders_pending_member_performance", "idx_performance_seats_reserved");
    }

    @Test
    void 중복_찜과_잘못된_판매_기간을_제약으로_거부한다() throws Exception {
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO likes (member_id, target_id, like_type, created_at, created_by) VALUES (1, 1, 'SHOW', CURRENT_TIMESTAMP, 'PG_TEST')");
            assertThatThrownBy(
                            () -> statement.execute(
                                    "INSERT INTO likes (member_id, target_id, like_type, created_at, created_by) VALUES (1, 1, 'SHOW', CURRENT_TIMESTAMP, 'PG_TEST')"))
                    .isInstanceOf(SQLException.class)
                    .extracting("SQLState")
                    .isEqualTo("23505");
            assertThatThrownBy(
                            () -> statement.execute(
                                    "INSERT INTO booking_performance_sales_policies (performance_id, order_opens_at, order_closes_at, hold_duration_seconds, created_at, created_by) VALUES (1, TIMESTAMP '2026-06-02 10:00:00', TIMESTAMP '2026-06-01 10:00:00', 600, CURRENT_TIMESTAMP, 'PG_TEST')"))
                    .isInstanceOf(SQLException.class)
                    .extracting("SQLState")
                    .isEqualTo("23514");
        }
    }

    @Test
    void 회차_등급의_중복과_음수_가격을_제약으로_거부한다() throws Exception {
        final String insertPerformanceGrade =
                "INSERT INTO performance_grades (performance_id, grade_id, price, sort_order, created_at, created_by) VALUES (9001, %d, %s, 1, CURRENT_TIMESTAMP, 'PG_TEST')";
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO performances (id, created_at, created_by) VALUES (9001, CURRENT_TIMESTAMP, 'PG_TEST')");
            statement.execute(
                    "INSERT INTO grades (id, code, name, created_at, created_by) VALUES (9001, 'PG_VIP', 'VIP석', CURRENT_TIMESTAMP, 'PG_TEST'), (9002, 'PG_R', 'R석', CURRENT_TIMESTAMP, 'PG_TEST')");
            statement.execute(insertPerformanceGrade.formatted(9001, "100000.00"));

            assertThatThrownBy(() -> statement.execute(insertPerformanceGrade.formatted(9001, "999.00")))
                    .isInstanceOf(SQLException.class)
                    .extracting("SQLState")
                    .isEqualTo("23505");
            assertThatThrownBy(() -> statement.execute(insertPerformanceGrade.formatted(9002, "-1.00")))
                    .isInstanceOf(SQLException.class)
                    .extracting("SQLState")
                    .isEqualTo("23514");
        }
    }

    /** entity 매핑으로 context를 띄워 Hibernate {@code validate}를 거친다. 스키마가 다르면 context 생성이 실패한다. */
    private static void validate(final String url) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SchemaValidation.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .properties(
                        "spring.datasource.url=" + url,
                        "spring.datasource.username=" + PostgreSqlTestDatabase.username(),
                        "spring.datasource.password=" + PostgreSqlTestDatabase.password(),
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.open-in-view=false",
                        "spring.flyway.enabled=false",
                        "logging.level.root=WARN")
                .run()) {
            context.getBeanFactory();
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, PostgreSqlTestDatabase.username(), PostgreSqlTestDatabase.password());
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EntityScan("com.ticket")
    static class SchemaValidation {}
}
