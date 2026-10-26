package com.ticket.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 운영 적재({@code seedProd})의 접속 설정 원본과 기본값을 고정한다.
 *
 * <p>가장 중요한 것은 <b>운영 접속 설정이 없으면 로컬 DB로 대체하지 않고 실패한다</b>는 것이다. 운영에 넣으려던 데이터가 조용히 로컬 DB에 들어가는 것이 이 프로그램에서 가장 나쁜 결과다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeedProdSettingsTest {
    private static final Map<String, String> PROD_ENVIRONMENT = Map.of(
            SeedSettings.DATASOURCE_URL_ENV, "jdbc:postgresql://ticket-db.example.com:5432/ticket",
            SeedSettings.DATASOURCE_USERNAME_ENV, "TICKET",
            SeedSettings.DATASOURCE_PASSWORD_ENV, "super-secret");

    @Test
    void 운영_접속_설정은_환경변수에서만_온다() {
        withCleanProperties(() -> {
            final SeedSettings settings = SeedSettings.forProd(PROD_ENVIRONMENT);

            assertThat(settings.target()).isEqualTo(SeedTarget.PROD);
            assertThat(settings.jdbcUrl()).isEqualTo("jdbc:postgresql://ticket-db.example.com:5432/ticket");
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
                        Map.of(SeedSettings.DATASOURCE_URL_ENV, "jdbc:postgresql://ticket-db.example.com:5432/ticket")))
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
    void 운영에서도_옵션으로_테스트_회원_수를_지정할_수_있다() {
        withCleanProperties(() -> {
            System.setProperty("seed.load-test-members.count", "10");
            System.setProperty("seed.load-test-fixture.performance-count", "2");
            final SeedSettings settings = SeedSettings.forProd(PROD_ENVIRONMENT);

            assertThat(settings.loadTestMemberCount()).isEqualTo(10);
            assertThat(settings.loadTestPerformanceCount()).isEqualTo(2);
        });
    }

    /** {@code seed.*} 프로퍼티는 실행 경로의 일부다. 다른 테스트가 남긴 값이 섞이지 않게 비우고 끝나면 되돌린다. */
    private static void withCleanProperties(final Runnable body) {
        final Map<String, String> previous = new HashMap<>();
        for (final String name : List.of(
                "seed.jdbc-url",
                "seed.jdbc-username",
                "seed.jdbc-password",
                "seed.load-test-members.count",
                "seed.load-test-fixture.performance-count")) {
            previous.put(name, System.getProperty(name));
            System.clearProperty(name);
        }
        try {
            body.run();
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }
}
