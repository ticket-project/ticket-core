package com.ticket.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ticket.seed.support.AppSchema;
import com.ticket.seed.support.SeedTestDatabase;
import com.ticket.show.domain.show.Show;

/**
 * 실제 PostgreSQL에서 운영 seed 진입점({@code seedProd})·재실행·identity·TEXT 매핑을 함께 검증한다. 공용 시드 전체 적재는 {@link SeedLocalTest}가 한 번
 * 담당한다 — 같은 적재 코드라 여기서 다시 돌리지 않는다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeedPostgreSqlTest {
    private static String url;

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        url = AppSchema.create("seed_prod");
        jdbc = new JdbcTemplate(SeedTestDatabase.dataSource(url));
    }

    @Test
    void 운영_시드를_재실행해도_중복되지_않고_생성_ID가_충돌하지_않는다() {
        Map<String, String> properties = new HashMap<>();
        properties.put("seed.sql-path", SeedTestPaths.minimalSeedSql().toString());
        properties.put("seed.load-test-members.count", "3");
        properties.put("seed.load-test-fixture.performance-count", "1");
        properties.put("seed.load-test-fixture.large-performance-count", "0");
        properties.put("seed.background-orders.count", "6");
        Map<String, String> previous = SeedSystemProperties.set(properties);
        try {
            Map<String, String> environment = Map.of(
                    SeedSettings.DATASOURCE_URL_ENV, url,
                    SeedSettings.DATASOURCE_USERNAME_ENV, SeedTestDatabase.username(),
                    SeedSettings.DATASOURCE_PASSWORD_ENV, SeedTestDatabase.password());
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
            try (ConfigurableApplicationContext context = AppSchema.openContext(url);
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
}
