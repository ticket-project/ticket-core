package com.ticket.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 운영 적재({@code seedProd})의 접속 설정 원본과 기본값을 고정한다.
 *
 * <p>가장 중요한 것은 <b>운영 접속 설정이 없으면 로컬 DB로 대체하지 않고 실패한다</b>는 것이다. 운영에 넣으려던 데이터가 조용히 로컬 H2에 들어가는 것이 이 프로그램에서 가장 나쁜 결과다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeedProdSettingsTest {
    private static final Map<String, String> PROD_ENVIRONMENT = Map.of(
            SeedSettings.DATASOURCE_URL_ENV, "jdbc:oracle:thin:@ticketdb_high",
            SeedSettings.DATASOURCE_USERNAME_ENV, "TICKET",
            SeedSettings.DATASOURCE_PASSWORD_ENV, "super-secret");

    @Test
    void 운영_접속_설정은_환경변수에서만_온다() {
        withCleanProperties(() -> {
            final SeedSettings settings = SeedSettings.forProd(PROD_ENVIRONMENT);

            assertThat(settings.target()).isEqualTo(SeedTarget.PROD);
            assertThat(settings.jdbcUrl()).isEqualTo("jdbc:oracle:thin:@ticketdb_high");
            assertThat(settings.jdbcUsername()).isEqualTo("TICKET");
            assertThat(settings.jdbcPassword()).isEqualTo("super-secret");
        });
    }

    @Test
    void 운영_접속_설정이_없으면_로컬_DB로_대체하지_않고_실패한다() {
        withCleanProperties(() -> assertThatThrownBy(() -> SeedSettings.forProd(Map.of()))
                .isInstanceOf(SeedFailure.class)
                .hasMessageContaining("운영 DB 접속 설정이 없습니다")
                .hasMessageContaining(SeedSettings.DATASOURCE_URL_ENV)
                .hasMessageContaining(SeedSettings.DATASOURCE_USERNAME_ENV)
                .hasMessageContaining(SeedSettings.DATASOURCE_PASSWORD_ENV));
    }

    @Test
    void 운영_접속_설정이_일부만_있어도_실패한다() {
        withCleanProperties(() -> assertThatThrownBy(() -> SeedSettings.forProd(
                        Map.of(SeedSettings.DATASOURCE_URL_ENV, "jdbc:oracle:thin:@ticketdb_high")))
                .isInstanceOf(SeedFailure.class)
                .hasMessageContaining(SeedSettings.DATASOURCE_USERNAME_ENV)
                .hasMessageContaining(SeedSettings.DATASOURCE_PASSWORD_ENV));
    }

    @Test
    void 운영_기본값은_공연_데이터만_적재한다() {
        withCleanProperties(() -> {
            final SeedSettings settings = SeedSettings.forProd(PROD_ENVIRONMENT);

            assertThat(settings.loadTestMemberCount()).isZero();
            assertThat(settings.loadTestPerformanceCount()).isZero();
        });
    }

    @Test
    void 로컬_기본값은_그대로_유지된다() {
        withCleanProperties(() -> {
            System.setProperty("seed.jdbc-url", "jdbc:h2:mem:seed-prod-settings");
            final SeedSettings settings = SeedSettings.forLocal(Map.of());

            assertThat(settings.target()).isEqualTo(SeedTarget.LOCAL);
            assertThat(settings.loadTestMemberCount()).isEqualTo(SeedSettings.DEFAULT_LOAD_TEST_MEMBER_COUNT);
            assertThat(settings.loadTestPerformanceCount()).isEqualTo(SeedSettings.DEFAULT_LOAD_TEST_PERFORMANCE_COUNT);
            assertThat(settings.loadTestMemberPassword()).isEqualTo(SeedSettings.DEFAULT_LOAD_TEST_MEMBER_PASSWORD);
        });
    }

    @Test
    void 운영에_테스트_회원을_만들려면_비밀번호_환경변수가_필요하다() {
        withCleanProperties(() -> {
            System.setProperty("seed.load-test-members.count", "10");

            assertThatThrownBy(() -> SeedSettings.forProd(PROD_ENVIRONMENT))
                    .isInstanceOf(SeedFailure.class)
                    .hasMessageContaining(SeedSettings.MEMBER_PASSWORD_ENV);
        });
    }

    @Test
    void 운영에_비밀번호를_명시하면_테스트_회원_수를_지정할_수_있다() {
        withCleanProperties(() -> {
            System.setProperty("seed.load-test-members.count", "10");
            System.setProperty("seed.load-test-fixture.performance-count", "2");
            final Map<String, String> environment = Map.of(
                    SeedSettings.DATASOURCE_URL_ENV,
                    "jdbc:oracle:thin:@ticketdb_high",
                    SeedSettings.DATASOURCE_USERNAME_ENV,
                    "TICKET",
                    SeedSettings.DATASOURCE_PASSWORD_ENV,
                    "super-secret",
                    SeedSettings.MEMBER_PASSWORD_ENV,
                    "명시한-비밀번호");

            final SeedSettings settings = SeedSettings.forProd(environment);

            assertThat(settings.loadTestMemberCount()).isEqualTo(10);
            assertThat(settings.loadTestPerformanceCount()).isEqualTo(2);
            assertThat(settings.loadTestMemberPassword()).isEqualTo("명시한-비밀번호");
            assertThat(settings.loadTestMemberPassword())
                    .as("운영에서는 저장소에 적힌 로컬 기본 비밀번호를 쓰지 않는다")
                    .isNotEqualTo(SeedSettings.DEFAULT_LOAD_TEST_MEMBER_PASSWORD);
        });
    }

    @Test
    void 운영_접속_URL의_자격증명은_출력에서_가려진다() {
        assertThat(SeedConsole.maskedUrl("jdbc:oracle:thin:@//host:1521/svc?user=TICKET&password=super-secret"))
                .doesNotContain("super-secret", "TICKET")
                .contains("password=***");
    }

    @Test
    void URL로_JDBC_드라이버를_고른다() {
        assertThat(SeedDataSources.driverClassNameFor("jdbc:oracle:thin:@ticketdb_high"))
                .isEqualTo("oracle.jdbc.OracleDriver");
        assertThat(SeedDataSources.driverClassNameFor("jdbc:h2:mem:x")).isEqualTo("org.h2.Driver");
        assertThatThrownBy(() -> SeedDataSources.driverClassNameFor("jdbc:postgresql://host/db"))
                .isInstanceOf(SeedFailure.class)
                .hasMessageContaining("지원하지 않는 JDBC URL");
    }

    /** {@code seed.*} 프로퍼티는 실행 경로의 일부다. 다른 테스트가 남긴 값이 섞이지 않게 비우고 끝나면 되돌린다. */
    private static void withCleanProperties(final Runnable body) {
        final Map<String, String> previous = SeedSystemProperties.set(
                Map.of("seed.project-dir", SeedTestPaths.projectDir().toString()));
        final String url = System.getProperty("seed.jdbc-url");
        final String username = System.getProperty("seed.jdbc-username");
        final String password = System.getProperty("seed.jdbc-password");
        final String memberCount = System.getProperty("seed.load-test-members.count");
        final String memberPassword = System.getProperty("seed.load-test-member-password");
        final String performanceCount = System.getProperty("seed.load-test-fixture.performance-count");
        System.clearProperty("seed.jdbc-url");
        System.clearProperty("seed.jdbc-username");
        System.clearProperty("seed.jdbc-password");
        System.clearProperty("seed.load-test-members.count");
        System.clearProperty("seed.load-test-member-password");
        System.clearProperty("seed.load-test-fixture.performance-count");
        try {
            body.run();
        } finally {
            restore("seed.jdbc-url", url);
            restore("seed.jdbc-username", username);
            restore("seed.jdbc-password", password);
            restore("seed.load-test-members.count", memberCount);
            restore("seed.load-test-member-password", memberPassword);
            restore("seed.load-test-fixture.performance-count", performanceCount);
            SeedSystemProperties.restore(previous);
        }
    }

    private static void restore(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
