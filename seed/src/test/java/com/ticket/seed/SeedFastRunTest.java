package com.ticket.seed;

import com.ticket.seed.support.AppSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 앱 스키마 위에서 시드 실행 경로를 빠르게 확인한다. 데이터 양과 무관한 것 — 실행 순서,
 * 완전성 판정, 트랜잭션 롤백, 동시 접속, 실패 보고 — 만 본다.
 *
 * <p>공용 시드 SQL은 {@code -Dseed.sql-path}로 테스트용 최소 파일을 지정한다. 100만 행에 가까운
 * 실제 시드 파일로 같은 것을 반복 확인하면 테스트가 몇 분 단위로 늘어난다 — 실제 파일 전체
 * 적재는 {@link SeedLocalTest}가 한 번 담당한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeedFastRunTest {

    private static final long FIXTURE_ID_BASE = 910000000L;

    @TempDir
    Path tempDir;

    private String jdbcUrl;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareSchema() {
        jdbcUrl = AppSchema.createIn(tempDir, "fast-" + UUID.randomUUID().toString().substring(0, 8));
        jdbcTemplate = new JdbcTemplate(dataSource(jdbcUrl));
    }

    @Test
    void 준비되지_않은_DB에서는_명확하게_실패한다() {
        final String emptyDatabaseUrl = AppSchema.urlFor(tempDir, "no-schema");

        assertThat(runSeed(Map.of("seed.jdbc-url", emptyDatabaseUrl)))
                .as("스키마가 없으면 적재를 시작하지 않고 0이 아닌 종료 코드로 끝난다")
                .isEqualTo(1);

        assertThatThrownBy(() -> SeedPreconditions.verify(dataSource(emptyDatabaseUrl), emptyDatabaseUrl))
                .isInstanceOf(SeedFailure.class)
                .hasMessageContaining("시드에 필요한 테이블이 없습니다")
                .hasMessageContaining("CATEGORIES")
                .hasMessageContaining("MEMBERS")
                .hasMessageContaining("local 프로파일로 서버를 먼저 기동");
    }

    @Test
    void 접속할_수_없는_DB는_원인과_함께_실패한다() {
        final String unreachableUrl = "jdbc:h2:file:" + tempDir.resolve("nope").toAbsolutePath()
                .toString().replace('\\', '/') + ";IFEXISTS=TRUE";

        assertThatThrownBy(() -> SeedPreconditions.verify(dataSource(unreachableUrl), unreachableUrl))
                .isInstanceOf(SeedFailure.class)
                .hasMessageContaining("DB에 접속할 수 없습니다");
    }

    @Test
    void 서버가_같은_파일_DB에_접속한_상태에서도_적재할_수_있다() throws SQLException {
        // 로컬 프로파일과 같은 AUTO_SERVER=TRUE 파일 DB. 앱이 붙어 있는 동안 시드가 같은 DB에
        // 두 번째 접속으로 붙는 상황을 그대로 만든다.
        final String sharedUrl = AppSchema.createIn(tempDir, "shared-db", ";AUTO_SERVER=TRUE");

        try (Connection applicationConnection = DriverManager.getConnection(sharedUrl, "sa", "")) {
            assertThat(applicationConnection.isClosed()).isFalse();

            assertThat(runSeed(Map.of("seed.jdbc-url", sharedUrl)))
                    .as("앱이 접속한 상태에서도 시드가 붙어 적재해야 한다")
                    .isZero();

            // 앱 쪽 연결에서도 방금 적재한 데이터가 보인다.
            try (var statement = applicationConnection.createStatement();
                 var resultSet = statement.executeQuery("SELECT COUNT(*) FROM CATEGORIES")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isPositive();
            }
        }
    }

    @Test
    void 빈_스키마에_적재하고_다시_실행해도_중복되지_않는다() {
        assertThat(runSeed(Map.of("seed.jdbc-url", jdbcUrl))).isZero();

        final Map<String, Long> afterFirstRun = snapshot();
        assertThat(afterFirstRun.get("CATEGORIES")).isEqualTo(1L);
        assertThat(afterFirstRun.get("SEATS")).isEqualTo(6L + 2000L); // 템플릿 3 x 공연장 2 + 부하 픽스처 2000
        assertThat(afterFirstRun.get("GRADES")).isEqualTo(4L);
        assertThat(afterFirstRun.get("MEMBERS")).isEqualTo(3L);

        assertThat(runSeed(Map.of("seed.jdbc-url", jdbcUrl)))
                .as("이미 적재된 상태에서도 정상 종료")
                .isZero();
        assertThat(snapshot()).isEqualTo(afterFirstRun);
    }

    @Test
    void 부분_적재_상태는_정상_완료로_처리하지_않는다() {
        assertThat(runSeed(Map.of("seed.jdbc-url", jdbcUrl))).isZero();

        // 뒤쪽 테이블만 비운다. 예전 판정(CATEGORIES에 행이 있는지)은 이 상태를 "이미 적재됨"으로
        // 읽고 조용히 넘어갔다.
        jdbcTemplate.update("DELETE FROM PERFORMANCE_SEATS WHERE performance_id < " + FIXTURE_ID_BASE);

        assertThat(runSeed(Map.of("seed.jdbc-url", jdbcUrl)))
                .as("부분 적재는 실패로 알린다")
                .isEqualTo(1);

        assertThat(count("SHOWS"))
                .as("자동으로 지우거나 복구하지 않는다")
                .isPositive();
    }

    @Test
    void 적재_실패는_해당_트랜잭션을_롤백한다() {
        // PERFORMANCE_SEATS를 미리 채워 두면 마지막 INSERT ... SELECT가 PK 중복으로 실패한다.
        // 앞에서 실행된 CATEGORIES 등 같은 트랜잭션의 INSERT가 함께 되돌아가야 한다.
        jdbcTemplate.update("""
                INSERT INTO PERFORMANCE_SEATS
                  (performance_id, seat_id, state, performance_grade_id, unit_price, version, created_at, created_by)
                VALUES (1, 1, 'AVAILABLE', 1, 1000, 0, CURRENT_TIMESTAMP, 'test')
                """);

        final SeedSettings settings = settings(Map.of("seed.jdbc-url", jdbcUrl));
        final DataSource dataSource = dataSource(jdbcUrl);
        final TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        final SeedTask curatedSeed = SeedLocalMain
                .tasks(new JdbcTemplate(dataSource), transactionTemplate, settings)
                .getFirst();

        assertThatThrownBy(curatedSeed::run).isInstanceOf(RuntimeException.class);

        assertThat(count("CATEGORIES"))
                .as("실패한 트랜잭션의 INSERT는 하나도 남지 않아야 한다")
                .isZero();
        assertThat(count("SHOWS")).isZero();
        assertThat(count("PERFORMANCE_SEATS"))
                .as("테스트가 미리 넣은 한 행만 남는다")
                .isEqualTo(1L);
    }

    @Test
    void 작업_순서는_공용_시드_다음에_부하_픽스처_그다음_회원이다() {
        final DataSource dataSource = dataSource(jdbcUrl);
        final List<SeedTask> tasks = SeedLocalMain.tasks(
                new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                settings(Map.of("seed.jdbc-url", jdbcUrl)));

        assertThat(tasks).hasExactlyElementsOfTypes(
                CuratedSeedLoader.class, LoadTestFixtureSeeder.class, LoadTestMemberSeeder.class);
    }

    @Test
    void 부하_픽스처와_회원은_0으로_지정하면_적재하지_않는다() {
        assertThat(runSeed(Map.of(
                "seed.jdbc-url", jdbcUrl,
                "seed.load-test-fixture.performance-count", "0",
                "seed.load-test-members.count", "0"))).isZero();

        assertThat(count("PERFORMANCES", "id >= " + FIXTURE_ID_BASE)).isZero();
        assertThat(count("MEMBERS")).isZero();
        assertThat(count("CATEGORIES"))
                .as("공용 시드는 그대로 적재된다")
                .isEqualTo(1L);
    }

    @Test
    void 이미_있는_회원_이메일은_건너뛴다() {
        assertThat(runSeed(Map.of("seed.jdbc-url", jdbcUrl, "seed.load-test-members.count", "2"))).isZero();
        assertThat(count("MEMBERS")).isEqualTo(2L);

        // 요청 수를 늘리면 없는 것만 새로 만든다.
        assertThat(runSeed(Map.of("seed.jdbc-url", jdbcUrl, "seed.load-test-members.count", "4"))).isZero();
        assertThat(count("MEMBERS")).isEqualTo(4L);
        assertThat(count("MEMBERS", "email = 'loadtest1@test.com'")).isEqualTo(1L);
    }

    /** 실제 {@code seedLocal}과 같은 경로로 실행한다. 최소 SQL과 임시 DB만 프로퍼티로 바꾼다. */
    private int runSeed(final Map<String, String> overrides) {
        final Map<String, String> previous = SeedSystemProperties.set(defaults(overrides));
        try {
            return SeedLocalMain.execute();
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    private SeedSettings settings(final Map<String, String> overrides) {
        final Map<String, String> previous = SeedSystemProperties.set(defaults(overrides));
        try {
            return SeedSettings.load();
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    private Map<String, String> defaults(final Map<String, String> overrides) {
        final Map<String, String> properties = new HashMap<>();
        properties.put("seed.project-dir", SeedTestPaths.projectDir().toString());
        properties.put("seed.sql-path", SeedTestPaths.minimalSeedSql().toString());
        properties.put("seed.load-test-fixture.performance-count", "1");
        properties.put("seed.load-test-members.count", "3");
        properties.putAll(overrides);
        return properties;
    }

    private static DataSource dataSource(final String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, "sa", "");
    }

    private Map<String, Long> snapshot() {
        return Map.of(
                "CATEGORIES", count("CATEGORIES"),
                "VENUES", count("VENUES"),
                "SHOWS", count("SHOWS"),
                "SEATS", count("SEATS"),
                "PERFORMANCES", count("PERFORMANCES"),
                "PERFORMANCE_GRADES", count("PERFORMANCE_GRADES"),
                "PERFORMANCE_SEATS", count("PERFORMANCE_SEATS"),
                "BOOKING_PERFORMANCE_SALES_POLICIES", count("BOOKING_PERFORMANCE_SALES_POLICIES"),
                "GRADES", count("GRADES"),
                "MEMBERS", count("MEMBERS")
        );
    }

    private long count(final String table) {
        return count(table, null);
    }

    private long count(final String table, final String where) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + (where == null ? "" : " WHERE " + where), Long.class);
        return count == null ? 0L : count;
    }
}
