package com.ticket.seed;

import com.ticket.member.account.domain.EncodedPassword;
import com.ticket.member.auth.application.PasswordHasher;
import com.ticket.member.auth.domain.RawPassword;
import com.ticket.member.auth.infrastructure.SpringSecurityPasswordHasher;
import com.ticket.seed.support.AppSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code seedLocal}의 <b>실제 실행 경로</b>를 임시 H2 파일 DB에서 통째로 돌린다.
 * ({@link SeedLocalMain#execute()}가 그 진입점이며, 여기서 호출하는 것이 운영 명령과 같은 코드다.)
 *
 * <p>스키마는 손으로 쓴 DDL이 아니라 실제 앱 entity 매핑으로 만든다({@link AppSchema}) — 그래서
 * NOT NULL·unique 제약을 포함한 진짜 제약 아래서 적재가 성립하는지를 본다.
 *
 * <p>공용 시드 전체 적재는 100만 행에 가까워 한 번만 돌린다. 그래서 이 클래스는 하나의 DB를
 * 공유하며 순서를 고정한다: 빈 스키마 적재 → 관계 검증 → 재실행 멱등성 → 회원 인증 호환성 →
 * (마지막) 부분 적재 감지. 마지막 테스트는 일부러 데이터를 지우므로 반드시 끝에 둔다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("NonAsciiCharacters")
class SeedLocalTest {

    private static final long FIXTURE_ID_BASE = 910000000L;
    private static final int FIXTURE_PERFORMANCE_COUNT = 8;
    private static final int FIXTURE_SEAT_COUNT = 2000;
    private static final int MEMBER_COUNT = 25;

    @TempDir
    static Path tempDir;

    private static String jdbcUrl;
    private static JdbcTemplate jdbcTemplate;
    private static Map<String, String> restoredProperties;

    @BeforeAll
    static void prepare() {
        jdbcUrl = AppSchema.createIn(tempDir, "seed-local");
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, "sa", ""));

        restoredProperties = SeedSystemProperties.set(Map.of(
                "seed.jdbc-url", jdbcUrl,
                // 회원 2,000명은 이 테스트가 확인하려는 것(형식·중복·인증 호환성)과 무관하게 느리다.
                // 기본값 2,000은 SeedSettingsTest가 따로 고정한다.
                "seed.load-test-members.count", String.valueOf(MEMBER_COUNT),
                "seed.load-test-fixture.performance-count", String.valueOf(FIXTURE_PERFORMANCE_COUNT)
        ));
    }

    @AfterAll
    static void restore() {
        SeedSystemProperties.restore(restoredProperties);
    }

    @Test
    @Order(1)
    void 빈_스키마에_공용_데이터와_부하_테스트_데이터를_적재한다() {
        assertThat(SeedLocalMain.execute())
                .as("정상 적재는 종료 코드 0")
                .isZero();

        assertThat(count("CATEGORIES")).isEqualTo(3);
        assertThat(count("GENRES")).isEqualTo(15);
        assertThat(curatedCount("VENUES", "id")).isPositive();
        assertThat(curatedCount("SHOWS", "id")).isPositive();
        assertThat(curatedCount("PERFORMANCES", "id")).isPositive();
        assertThat(curatedCount("PERFORMANCE_SEATS", "performance_id")).isPositive();

        // 공용 시드가 만든 회차 전부에 판매정책과 등급 4개가 붙는다(ADR 0005·0006).
        final long curatedPerformances = curatedCount("PERFORMANCES", "id");
        assertThat(curatedCount("BOOKING_PERFORMANCE_SALES_POLICIES", "performance_id"))
                .isEqualTo(curatedPerformances);
        assertThat(curatedCount("PERFORMANCE_GRADES", "performance_id"))
                .isEqualTo(curatedPerformances * 4);

        // 부하 테스트 전용 고정 ID 대역: 회차 8개, 물리 좌석 2,000석.
        assertThat(count("SEATS", "id BETWEEN " + (FIXTURE_ID_BASE + 1) + " AND " + (FIXTURE_ID_BASE + FIXTURE_SEAT_COUNT)))
                .isEqualTo(FIXTURE_SEAT_COUNT);
        assertThat(count("PERFORMANCES", "id >= " + FIXTURE_ID_BASE)).isEqualTo(FIXTURE_PERFORMANCE_COUNT);
        assertThat(count("PERFORMANCE_SEATS", "performance_id >= " + FIXTURE_ID_BASE))
                .isEqualTo((long) FIXTURE_PERFORMANCE_COUNT * FIXTURE_SEAT_COUNT);
        assertThat(count("BOOKING_PERFORMANCE_SALES_POLICIES",
                "performance_id >= " + FIXTURE_ID_BASE + " AND queue_mode = 'FORCE_OFF'"))
                .isEqualTo(FIXTURE_PERFORMANCE_COUNT);
        assertThat(count("MEMBERS", "email LIKE 'loadtest%@test.com'")).isEqualTo(MEMBER_COUNT);
    }

    @Test
    @Order(2)
    void 공연장_회차_좌석_등급_가격_판매정책_관계가_유지된다() {
        assertThat(count("PERFORMANCE_SEATS ps"
                + " JOIN PERFORMANCES p ON p.id = ps.performance_id"
                + " JOIN SHOWS sh ON sh.id = p.show_id"
                + " JOIN SEATS st ON st.id = ps.seat_id", "st.venue_id <> sh.venue_id"))
                .as("회차좌석의 물리 좌석은 그 공연의 공연장에 속해야 한다")
                .isZero();

        assertThat(count("PERFORMANCE_SEATS ps"
                + " LEFT JOIN PERFORMANCE_GRADES pg ON pg.id = ps.performance_grade_id",
                "pg.id IS NULL OR pg.performance_id <> ps.performance_id"))
                .as("회차좌석의 등급은 같은 회차의 PERFORMANCE_GRADES여야 한다")
                .isZero();

        assertThat(count("PERFORMANCE_SEATS ps"
                + " JOIN PERFORMANCE_GRADES pg ON pg.id = ps.performance_grade_id",
                "ps.unit_price <> pg.price"))
                .as("회차좌석 단가의 원본은 PerformanceGrade.price다(ADR 0005)")
                .isZero();

        assertThat(count("PERFORMANCE_GRADES pg LEFT JOIN GRADES g ON g.id = pg.grade_id", "g.id IS NULL"))
                .as("회차 등급은 실재하는 GRADES 코드를 가리켜야 한다")
                .isZero();

        assertThat(count("PERFORMANCES p"
                + " LEFT JOIN BOOKING_PERFORMANCE_SALES_POLICIES sp ON sp.performance_id = p.id",
                "sp.performance_id IS NULL"))
                .as("모든 회차에 예매 판매정책이 있어야 한다")
                .isZero();

        assertThat(count("SHOWS sh LEFT JOIN VENUES v ON v.id = sh.venue_id",
                "sh.venue_id IS NOT NULL AND v.id IS NULL"))
                .as("공연은 실재하는 공연장을 가리켜야 한다")
                .isZero();

        assertThat(count("GRADES", "code IN ('VIP', 'R', 'S', 'A')"))
                .as("등급 코드는 code당 하나만 있어야 한다")
                .isEqualTo(4);

        // 부하 테스트 전용 데이터의 등급별 가격(구역 1~2 VIP 150000, 마지막 구역 A 60000).
        assertThat(count("PERFORMANCE_SEATS",
                "performance_id = " + (FIXTURE_ID_BASE + 1) + " AND unit_price = 150000")).isEqualTo(400);
        assertThat(count("PERFORMANCE_SEATS",
                "performance_id = " + (FIXTURE_ID_BASE + 1) + " AND unit_price = 60000")).isEqualTo(600);
    }

    @Test
    @Order(3)
    void 같은_명령을_다시_실행해도_데이터가_중복되지_않는다() {
        final Map<String, Long> before = snapshot();

        assertThat(SeedLocalMain.execute())
                .as("이미 적재된 상태에서도 정상 종료")
                .isZero();

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @Order(4)
    void 생성된_테스트_회원이_실제_앱_인증_경로와_호환된다() {
        final String email = "loadtest1@test.com";
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT password, name, role, deleted_at FROM MEMBERS WHERE email = ?", email);

        assertThat(row.get("ROLE")).isEqualTo("MEMBER");
        assertThat(row.get("NAME")).isEqualTo("loadtest1");
        assertThat(row.get("DELETED_AT")).isNull();

        final String stored = (String) row.get("PASSWORD");
        assertThat(stored)
                .as("앱의 DelegatingPasswordEncoder가 읽는 접두사 형식이어야 한다")
                .startsWith("{bcrypt}$2");

        // 앱이 실제로 로그인 검증에 쓰는 구현으로 그대로 검증한다.
        final PasswordHasher passwordHasher =
                new SpringSecurityPasswordHasher(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        assertThat(passwordHasher.matches(
                RawPassword.create(SeedSettings.DEFAULT_LOAD_TEST_MEMBER_PASSWORD),
                EncodedPassword.create(stored)))
                .as("시드가 넣은 해시가 앱 PasswordHasher로 검증돼야 한다")
                .isTrue();
        assertThat(passwordHasher.matches(RawPassword.create("wrong-password"), EncodedPassword.create(stored)))
                .as("틀린 비밀번호는 통과하지 않아야 한다")
                .isFalse();
    }

    @Test
    @Order(5)
    void 실제_앱_entity_매핑으로_시드_회원을_읽을_수_있다() {
        try (var context = AppSchema.openContext(jdbcUrl)) {
            final var entityManagerFactory =
                    context.getBean(jakarta.persistence.EntityManagerFactory.class);
            try (var entityManager = entityManagerFactory.createEntityManager()) {
                final var member = entityManager.createQuery(
                                "select m from Member m where m.email.email = :email",
                                com.ticket.member.account.domain.Member.class)
                        .setParameter("email", "loadtest1@test.com")
                        .getSingleResult();

                assertThat(member.getRole()).isEqualTo(com.ticket.member.account.domain.Role.MEMBER);
                assertThat(member.isDeleted()).isFalse();
                assertThat(member.getEncodedPassword().getPassword()).startsWith("{bcrypt}$2");
                assertThat(member.getCreatedAt()).isNotNull();
                assertThat(member.getCreatedBy()).isNotBlank();
            }
        }
    }

    /**
     * 반드시 마지막이다 — 데이터를 일부러 지운다.
     *
     * <p>예전 판정은 CATEGORIES에 행이 있는지 하나만 봤기 때문에, 뒤쪽 테이블이 통째로 비어도
     * "이미 적재됨"으로 넘어갔다. 지금은 테이블별 기대 행 수와 비교해 불일치를 실패로 만든다.
     */
    @Test
    @Order(6)
    void 불완전한_데이터_상태를_정상_완료로_처리하지_않는다() {
        final long removed = jdbcTemplate.update(
                "DELETE FROM PERFORMANCE_SEATS WHERE performance_id < " + FIXTURE_ID_BASE
                        + " AND MOD(seat_id, 2) = 0");
        assertThat(removed).isPositive();

        assertThat(SeedLocalMain.execute())
                .as("부분 적재 상태는 실패로 알린다")
                .isEqualTo(1);

        assertThat(curatedCount("PERFORMANCE_SEATS", "performance_id"))
                .as("자동으로 지우거나 채워 넣지 않는다")
                .isPositive();
    }

    private static Map<String, Long> snapshot() {
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
}
