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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ticket.testsupport.TestContainerImages;
import com.ticket.testsupport.persistence.MigratedSchema;

/** 로컬과 RDS가 사용하는 migration을 실제 PostgreSQL에 적용하고 매핑·제약·긴 문자열을 검증한다. */
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("NonAsciiCharacters")
class PostgreSqlMigrationChainSchemaTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestContainerImages.POSTGRESQL);

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        ModulithFlywayTestSupport.applyMigrations(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword(),
                "postgresql",
                MigratedSchema.MODULES_IN_RUNTIME_ORDER);
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void 스키마가_entity와_일치하고_migration을_재실행할_수_있다() {
        assertThatCode(() -> MigrationChainSchemaTest.validate(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
                .doesNotThrowAnyException();
        assertThatCode(() -> ModulithFlywayTestSupport.applyMigrations(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        "postgresql",
                        MigratedSchema.MODULES_IN_RUNTIME_ORDER))
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
            String schema = "slice_" + module;
            jdbc.execute("CREATE SCHEMA " + schema);
            String url = POSTGRES.getJdbcUrl() + "&currentSchema=" + schema;
            assertThatCode(() -> ModulithFlywayTestSupport.applyMigrations(
                            url, POSTGRES.getUsername(), POSTGRES.getPassword(), "postgresql", List.of(module)))
                    .doesNotThrowAnyException();
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class t ON t.oid = i.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = current_schema() AND NOT i.indisunique
                  AND t.relname NOT LIKE 'flyway_schema_history%'
                """, Integer.class)).isZero();
    }

    @Test
    void 중복_찜과_잘못된_판매_기간을_제약으로_거부한다() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
}
