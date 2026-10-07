package com.ticket.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ticket.seed.support.AppSchema;
import com.ticket.show.domain.show.Show;

/** 실제 PostgreSQL에서 운영 seed 진입점·재실행·identity·TEXT 매핑을 함께 검증한다. */
@Testcontainers(disabledWithoutDocker = true)
class SeedPostgreSqlTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        AppSchema.createOn(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void production_seed_is_repeatable_and_generated_ids_do_not_collide() {
        Map<String, String> properties = new HashMap<>();
        properties.put("seed.project-dir", SeedTestPaths.projectDir().toString());
        properties.put("seed.sql-path", SeedTestPaths.minimalSeedSql().toString());
        properties.put("seed.load-test-members.count", "3");
        properties.put("seed.load-test-fixture.performance-count", "1");
        properties.put("seed.load-test-fixture.large-performance-count", "0");
        properties.put("seed.background-orders.count", "6");
        Map<String, String> previous = SeedSystemProperties.set(properties);
        try {
            Map<String, String> environment = Map.of(
                    SeedSettings.DATASOURCE_URL_ENV, POSTGRES.getJdbcUrl(),
                    SeedSettings.DATASOURCE_USERNAME_ENV, POSTGRES.getUsername(),
                    SeedSettings.DATASOURCE_PASSWORD_ENV, POSTGRES.getPassword());
            assertThat(SeedProdMain.execute(environment)).isZero();
            Long showsBefore = jdbc.queryForObject("SELECT count(*) FROM shows", Long.class);
            Long seatsBefore = jdbc.queryForObject("SELECT count(*) FROM performance_seats", Long.class);
            Long ordersBefore = jdbc.queryForObject("SELECT count(*) FROM orders", Long.class);
            assertThat(ordersBefore).isEqualTo(6L);
            assertThat(SeedProdMain.execute(environment)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shows", Long.class))
                    .isEqualTo(showsBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM performance_seats", Long.class))
                    .isEqualTo(seatsBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class))
                    .isEqualTo(ordersBefore);

            Long maximum = jdbc.queryForObject("SELECT max(id) FROM shows", Long.class);
            String info = "긴 공연 설명".repeat(1000);
            Long id = jdbc.queryForObject(
                    "INSERT INTO shows (venue_id, view_count, info, created_at, created_by) VALUES (1, 0, ?, CURRENT_TIMESTAMP, 'PG_TEST') RETURNING id",
                    Long.class,
                    info);
            assertThat(id).isGreaterThan(maximum);
            try (ConfigurableApplicationContext context = AppSchema.openContext(
                            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    EntityManager entityManager =
                            context.getBean(EntityManagerFactory.class).createEntityManager()) {
                assertThat(entityManager.find(Show.class, id).getInfo()).isEqualTo(info);
            }
            new PostgreSqlIdentitySynchronizer(jdbc).run();
            Long next = jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('shows', 'id'))", Long.class);
            assertThat(next).isGreaterThan(id);
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    @Test
    void preconditions_do_not_accept_tables_in_another_schema() {
        jdbc.execute("CREATE SCHEMA seed_shadow");
        DriverManagerDataSource shadow = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=seed_shadow", POSTGRES.getUsername(), POSTGRES.getPassword());
        assertThatThrownBy(() -> SeedPreconditions.verify(shadow, POSTGRES.getJdbcUrl(), SeedTarget.PROD))
                .isInstanceOf(SeedFailure.class)
                .hasMessageContaining("없는 테이블", "seed_shadow");
    }

    @Test
    void full_curated_sql_loads_on_postgresql() {
        jdbc.execute("CREATE SCHEMA curated_full");
        String url = POSTGRES.getJdbcUrl() + "&currentSchema=curated_full";
        AppSchema.createOn(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        Map<String, String> previous = SeedSystemProperties.set(Map.of(
                "seed.project-dir", SeedTestPaths.projectDir().toString(),
                "seed.load-test-members.count", "0",
                "seed.load-test-fixture.performance-count", "0",
                "seed.load-test-fixture.large-performance-count", "0",
                "seed.background-orders.count", "0"));
        String previousSql = System.getProperty("seed.sql-path");
        System.clearProperty("seed.sql-path");
        try {
            assertThat(SeedProdMain.execute(Map.of(
                            SeedSettings.DATASOURCE_URL_ENV, url,
                            SeedSettings.DATASOURCE_USERNAME_ENV, POSTGRES.getUsername(),
                            SeedSettings.DATASOURCE_PASSWORD_ENV, POSTGRES.getPassword())))
                    .isZero();
            JdbcTemplate full =
                    new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
            Long performances = full.queryForObject("SELECT count(*) FROM performances", Long.class);
            assertThat(performances)
                    .isEqualTo(CuratedSeedStatements.from(
                                    SeedTestPaths.projectDir().resolve("seed/sql/kopis-curated.sql"))
                            .literalInsertCount("PERFORMANCES"));
            assertThat(full.queryForObject("SELECT count(*) FROM performance_seats", Long.class))
                    .isEqualTo(performances * 600);
        } finally {
            SeedSystemProperties.restore(previous);
            if (previousSql == null) System.clearProperty("seed.sql-path");
            else System.setProperty("seed.sql-path", previousSql);
        }
    }
}
