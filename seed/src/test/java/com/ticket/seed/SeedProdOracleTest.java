package com.ticket.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.ticket.seed.support.AppSchema;

/**
 * {@code seedProd}의 <b>실제 실행 경로</b>를 임시 Oracle에서 통째로 돌린다. ({@link SeedProdMain#execute(Map)}가 그 진입점이며, 환경변수만 주입할 뿐 나머지는
 * 운영 명령과 같은 코드다.)
 *
 * <p>H2 Oracle 모드로는 확인할 수 없는 것들이 여기 있다 — 실제 Oracle 방언, {@code USER_TABLES} 기준 스키마 판정, 세션 기본 날짜
 * 형식({@code NLS_DATE_FORMAT}) 의존성. 저장소에 이미 있는 {@code gvenzl/oracle-free:23-slim}을 그대로
 * 쓴다({@code com.ticket.bootstrap.migration.OracleMigrationCompatibilityTest} 참고).
 *
 * <p>스키마는 손으로 쓴 DDL이 아니라 <b>실제 앱 entity 매핑</b>으로 만든다({@link AppSchema#createOn}). 그 기능은 테스트 소스에만 있다 — 테이블 생성은
 * {@code seedProd}의 범위가 아니다.
 *
 * <p>공용 시드 전체 적재는 90만 행이 넘어 한 번만 돌린다. 그래서 하나의 스키마를 공유하며 순서를 고정한다. 마지막 테스트는 일부러 데이터를 지우므로 반드시 끝에 둔다.
 *
 * <p><b>운영 DB에는 접속하지 않는다.</b> 모든 접속 대상은 이 테스트가 띄운 컨테이너다. Docker가 없으면 클래스 전체가 건너뛰어진다.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("NonAsciiCharacters")
class SeedProdOracleTest {
    /**
     * 비밀번호는 출력에 없어야 한다는 검증을 실제로 하려면 값이 구별 가능해야 한다. 컨테이너 기본 비밀번호({@code test})는 계정명·경로 등 출력 곳곳에 우연히 들어 있는 문자열이라 그 검증이
     * 의미를 잃는다.
     */
    private static final String ORACLE_PASSWORD = "Seed_Pw_9f3a2b";

    private static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-free:23-slim").withPassword(ORACLE_PASSWORD);
    private static final long FIXTURE_ID_BASE = 910000000L;
    private static final int FIXTURE_PERFORMANCE_COUNT = 2;
    private static final int FIXTURE_SEAT_COUNT = 2000;
    private static final int MEMBER_COUNT = 5;
    private static final String MEMBER_PASSWORD = "운영에서만-주는-비밀번호";

    /** 좌석 템플릿 1벌의 크기다. 공연장마다 이만큼 복제된다. */
    private static final long SEATS_PER_VENUE = 600L;

    private static JdbcTemplate jdbcTemplate;
    private static Map<String, String> restoredProperties;

    @BeforeAll
    static void prepare() {
        ORACLE.start();
        AppSchema.createOn(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword());
        jdbcTemplate = new JdbcTemplate(
                new DriverManagerDataSource(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword()));

        // 운영 경로는 환경변수만 본다. 다른 테스트가 남긴 -Dseed.jdbc-* 가 섞이면 실행 경로가 달라진다.
        restoredProperties = SeedSystemProperties.set(
                Map.of("seed.project-dir", SeedTestPaths.projectDir().toString()));
        System.clearProperty("seed.jdbc-url");
        System.clearProperty("seed.jdbc-username");
        System.clearProperty("seed.jdbc-password");
        System.clearProperty("seed.sql-path");
        System.clearProperty("seed.load-test-members.count");
        System.clearProperty("seed.load-test-fixture.performance-count");
        System.clearProperty("seed.load-test-member-password");
    }

    @AfterAll
    static void restore() {
        SeedSystemProperties.restore(restoredProperties);
        ORACLE.stop();
    }

    @Test
    @Order(1)
    void 운영_접속_설정이_없으면_아무것도_적재하지_않는다() {
        assertThat(SeedProdMain.execute(Map.of())).as("환경변수가 없으면 실패로 끝난다").isEqualTo(1);

        assertThat(count("CATEGORIES")).as("접속조차 하지 않으므로 적재되지 않는다").isZero();
    }

    /**
     * Oracle에서 {@code DatabaseMetaData#getTables}는 접속 계정이 볼 수 있는 다른 스키마의 동명 테이블까지 돌려준다. 그것을 "준비됐다"고 읽으면 엉뚱한 스키마를 준비된
     * 것으로 오인한다 — 실제로 접속한 스키마({@code USER_TABLES})만 봐야 한다.
     */
    @Test
    @Order(2)
    void 다른_스키마의_동명_테이블을_준비된_것으로_보지_않는다() throws SQLException {
        final String otherUser = "SEED_OTHER_SCHEMA";
        final String otherPassword = "Other_Pw_1";
        try (Connection system = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword());
                Statement statement = system.createStatement()) {
            statement.execute("CREATE USER " + otherUser + " IDENTIFIED BY \"" + otherPassword + "\"");
            statement.execute("GRANT CREATE SESSION, CREATE TABLE, UNLIMITED TABLESPACE TO " + otherUser);
            // 앱 계정의 테이블을 볼 수 있게 한다. 이 상태에서도 '준비됐다'고 읽으면 안 된다.
            statement.execute("GRANT SELECT ANY TABLE TO " + otherUser);
        }

        try (Connection other = DriverManager.getConnection(ORACLE.getJdbcUrl(), otherUser, otherPassword);
                Statement statement = other.createStatement()) {
            statement.execute("CREATE TABLE CATEGORIES (id NUMBER PRIMARY KEY)");
        }

        assertThat(SeedProdMain.execute(environment(ORACLE.getJdbcUrl(), otherUser, otherPassword)))
                .as("자기 스키마에 시드 테이블이 없으면 적재를 시작하지 않는다")
                .isEqualTo(1);

        assertThatThrownBy(() -> SeedPreconditions.verify(
                        new DriverManagerDataSource(ORACLE.getJdbcUrl(), otherUser, otherPassword),
                        ORACLE.getJdbcUrl(),
                        SeedTarget.PROD))
                .isInstanceOf(SeedFailure.class)
                .hasMessageContaining("시드에 필요한 테이블이 없습니다")
                .hasMessageContaining("VENUES")
                .hasMessageContaining("seedProd는 이미 준비된 테이블에 데이터만 넣습니다");
    }

    /**
     * 세션 기본 날짜 형식과 무관하게 같은 값이 저장되는지 본다. Oracle은 {@code NLS_DATE_FORMAT}이 {@code DD-MON-RR}인 세션에서 ISO 문자열을 날짜로 읽지 못한다 —
     * 그래서 실행 문장은 {@code DATE '...'} / {@code TIMESTAMP '...'} 명시적 리터럴을 쓴다.
     */
    @Test
    @Order(3)
    void 세션_날짜_형식이_달라도_같은_값이_저장된다() throws SQLException {
        final String performanceInsert =
                CuratedSeedStatements.from(SeedTestPaths.minimalSeedSql()).executableStatements().stream()
                        .filter(statement -> statement.startsWith("INSERT INTO PERFORMANCES ("))
                        .findFirst()
                        .orElseThrow();
        assertThat(performanceInsert).as("실행 문장은 명시적 TIMESTAMP 리터럴을 쓴다").contains("TIMESTAMP '2026-06-01 14:00:00'");

        try (Connection connection =
                        DriverManager.getConnection(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE SEED_NLS_PROBE (
                      id NUMBER, show_id NUMBER, performance_no NUMBER,
                      start_time TIMESTAMP, end_time TIMESTAMP,
                      created_at TIMESTAMP, created_by VARCHAR2(50))
                    """);
            // 운영에서 만날 수 있는 비-ISO 기본 형식으로 세션을 바꾼다.
            statement.execute("ALTER SESSION SET NLS_DATE_FORMAT = 'DD-MON-RR'");
            statement.execute("ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'DD-MON-RR HH.MI.SSXFF AM'");

            statement.execute(performanceInsert.replace("INSERT INTO PERFORMANCES (", "INSERT INTO SEED_NLS_PROBE ("));

            try (ResultSet row =
                    statement.executeQuery("SELECT TO_CHAR(start_time, 'YYYY-MM-DD HH24:MI:SS') FROM SEED_NLS_PROBE")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("2026-06-01 14:00:00");
            }

            // 같은 세션에서 옛 방식(따옴표 문자열)은 실패한다 — 이 변환이 왜 필요한지가 여기서 드러난다.
            assertThatThrownBy(() -> statement.execute(
                            "INSERT INTO SEED_NLS_PROBE (id, start_time) VALUES" + " (999, '2026-06-01 14:00:00')"))
                    .isInstanceOf(SQLException.class);

            statement.execute("DROP TABLE SEED_NLS_PROBE");
        }
    }

    @Test
    @Order(4)
    void 운영_기본_실행은_공연_데이터만_적재한다() {
        final Output output = captureOutput(() -> SeedProdMain.execute(prodEnvironment()));

        assertThat(output.exitCode()).as("정상 적재는 종료 코드 0").isZero();
        assertThat(output.text()).as("접속 비밀번호는 어떤 경로로도 출력하지 않는다").doesNotContain(ORACLE.getPassword());

        assertThat(count("CATEGORIES")).isEqualTo(3L);
        assertThat(count("GENRES")).isEqualTo(15L);
        final long venues = curatedCount("VENUES", "id");
        final long performances = curatedCount("PERFORMANCES", "id");
        assertThat(venues).isEqualTo(literalInsertCount("VENUES"));
        assertThat(curatedCount("SHOWS", "id")).isEqualTo(literalInsertCount("SHOWS"));
        assertThat(performances).isEqualTo(literalInsertCount("PERFORMANCES"));
        assertThat(curatedCount("SEATS", "id")).as("모든 공연장이 좌석 템플릿을 복제받는다").isEqualTo(venues * SEATS_PER_VENUE);
        assertThat(curatedCount("PERFORMANCE_SEATS", "performance_id")).isEqualTo(performances * SEATS_PER_VENUE);
        assertThat(curatedCount("PERFORMANCE_GRADES", "performance_id")).isEqualTo(performances * 4);
        assertThat(curatedCount("BOOKING_PERFORMANCE_SALES_POLICIES", "performance_id"))
                .isEqualTo(performances);

        assertThat(count("MEMBERS")).as("운영 기본값은 테스트 회원을 만들지 않는다").isZero();
        assertThat(count("PERFORMANCES", "id >= " + FIXTURE_ID_BASE))
                .as("운영 기본값은 부하 테스트 공연을 만들지 않는다")
                .isZero();
        assertThat(count("SHOWS", "id >= " + FIXTURE_ID_BASE)).isZero();
    }

    @Test
    @Order(5)
    void 좌석_없는_공연장과_회차가_하나도_없다() {
        assertThat(count(
                        "VENUES v",
                        "v.id < "
                                + FIXTURE_ID_BASE
                                + " AND NOT EXISTS (SELECT 1 FROM SEATS s WHERE s.venue_id"
                                + " = v.id)"))
                .as("모든 공용 공연장에 물리 좌석이 있어야 한다")
                .isZero();
        assertThat(count(
                        "PERFORMANCES p",
                        "p.id < "
                                + FIXTURE_ID_BASE
                                + " AND NOT EXISTS (SELECT 1 FROM PERFORMANCE_SEATS ps"
                                + " WHERE ps.performance_id = p.id)"))
                .as("모든 공용 회차에 회차좌석이 있어야 한다")
                .isZero();
        assertThat(count(
                        "PERFORMANCE_SEATS ps"
                                + " JOIN PERFORMANCES p ON p.id = ps.performance_id"
                                + " JOIN SHOWS sh ON sh.id = p.show_id"
                                + " JOIN SEATS st ON st.id = ps.seat_id",
                        "st.venue_id <> sh.venue_id"))
                .as("회차좌석의 물리 좌석은 그 공연의 공연장에 속해야 한다")
                .isZero();
        assertThat(count(
                        "PERFORMANCE_SEATS ps"
                                + " LEFT JOIN PERFORMANCE_GRADES pg ON pg.id ="
                                + " ps.performance_grade_id",
                        "pg.id IS NULL OR pg.performance_id <> ps.performance_id"))
                .as("회차좌석의 등급은 같은 회차의 PERFORMANCE_GRADES여야 한다")
                .isZero();
        assertThat(count(
                        "PERFORMANCE_SEATS ps" + " JOIN PERFORMANCE_GRADES pg ON pg.id =" + " ps.performance_grade_id",
                        "ps.unit_price <> pg.price"))
                .as("회차좌석 단가의 원본은 PerformanceGrade.price다(ADR 0005)")
                .isZero();
        assertThat(count("GRADES", "code IN ('VIP', 'R', 'S', 'A')")).isEqualTo(4L);
    }

    @Test
    @Order(6)
    void 같은_명령을_다시_실행해도_중복되지_않는다() {
        final Map<String, Long> before = snapshot();

        assertThat(SeedProdMain.execute(prodEnvironment()))
                .as("이미 적재된 상태에서도 정상 종료")
                .isZero();

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @Order(7)
    void 옵션을_지정하면_테스트_회원과_부하_회차도_적재한다() {
        final Map<String, String> previous = SeedSystemProperties.set(Map.of(
                "seed.load-test-members.count",
                String.valueOf(MEMBER_COUNT),
                "seed.load-test-fixture.performance-count",
                String.valueOf(FIXTURE_PERFORMANCE_COUNT)));
        try {
            assertThat(SeedProdMain.execute(environmentWith(SeedSettings.MEMBER_PASSWORD_ENV, MEMBER_PASSWORD)))
                    .isZero();

            assertThat(count("MEMBERS", "email LIKE 'loadtest%@test.com'")).isEqualTo(MEMBER_COUNT);
            assertThat(count("PERFORMANCES", "id >= " + FIXTURE_ID_BASE)).isEqualTo(FIXTURE_PERFORMANCE_COUNT);
            assertThat(count("PERFORMANCE_SEATS", "performance_id >= " + FIXTURE_ID_BASE))
                    .isEqualTo((long) FIXTURE_PERFORMANCE_COUNT * FIXTURE_SEAT_COUNT);
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    @Test
    @Order(8)
    void 운영에_테스트_회원을_만들려면_비밀번호_환경변수가_필요하다() {
        final Map<String, String> previous = SeedSystemProperties.set(Map.of("seed.load-test-members.count", "3"));
        try {
            final Output output = captureOutput(() -> SeedProdMain.execute(prodEnvironment()));

            assertThat(output.exitCode()).isEqualTo(1);
            assertThat(output.text()).contains(SeedSettings.MEMBER_PASSWORD_ENV);
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    /** 반드시 마지막이다 — 데이터를 일부러 지운다. */
    @Test
    @Order(9)
    void 불완전한_데이터_상태를_정상_완료로_처리하지_않는다() {
        final int removed = jdbcTemplate.update(
                "DELETE FROM PERFORMANCE_SEATS WHERE performance_id < " + FIXTURE_ID_BASE + " AND MOD(seat_id, 2) = 0");
        assertThat(removed).isPositive();

        assertThat(SeedProdMain.execute(prodEnvironment()))
                .as("부분 적재 상태는 실패로 알린다")
                .isEqualTo(1);

        assertThat(curatedCount("PERFORMANCE_SEATS", "performance_id"))
                .as("자동으로 지우거나 채워 넣지 않는다")
                .isPositive();
    }

    private static long literalInsertCount(final String table) {
        return CuratedSeedStatements.from(SeedTestPaths.seedSql()).literalInsertCount(table);
    }

    private static Map<String, String> prodEnvironment() {
        return environment(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword());
    }

    private static Map<String, String> environmentWith(final String name, final String value) {
        final Map<String, String> environment = new HashMap<>(prodEnvironment());
        environment.put(name, value);
        return environment;
    }

    private static Map<String, String> environment(final String url, final String username, final String password) {
        return Map.of(
                SeedSettings.DATASOURCE_URL_ENV, url,
                SeedSettings.DATASOURCE_USERNAME_ENV, username,
                SeedSettings.DATASOURCE_PASSWORD_ENV, password);
    }

    private static Map<String, Long> snapshot() {
        final Map<String, Long> counts = new HashMap<>();
        for (final String table : List.of(
                "CATEGORIES",
                "GENRES",
                "PERFORMERS",
                "VENUES",
                "SHOWS",
                "SHOW_GENRES",
                "SEATS",
                "PERFORMANCES",
                "PERFORMANCE_GRADES",
                "PERFORMANCE_SEATS",
                "BOOKING_PERFORMANCE_SALES_POLICIES",
                "GRADES",
                "MEMBERS")) {
            counts.put(table, count(table));
        }
        return counts;
    }

    private static long curatedCount(final String table, final String idColumn) {
        return count(table, idColumn + " < " + FIXTURE_ID_BASE);
    }

    private static long count(final String from) {
        return count(from, null);
    }

    private static long count(final String from, final String where) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + from + (where == null ? "" : " WHERE " + where), Long.class);
        return count == null ? 0L : count;
    }

    /** 시드 프로그램은 표준 출력이 곧 결과 보고다. 비밀번호가 새지 않는지 보려면 그 출력을 그대로 봐야 한다. */
    private static Output captureOutput(final java.util.function.IntSupplier body) {
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        try {
            System.setOut(capture);
            System.setErr(capture);
            final int exitCode = body.getAsInt();
            capture.flush();
            return new Output(exitCode, buffer.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record Output(int exitCode, String text) {}
}
