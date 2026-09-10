package com.ticket.seed;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 접속 설정의 원본과 기본값을 고정한다.
 *
 * <p>가장 중요한 것은 <b>앱과 시드가 같은 DB를 본다</b>는 것이다 — URL을 시드 쪽에 따로 적어 두면
 * 언젠가 반드시 어긋난다. 그래서 원본은 {@code src/main/resources/application-local.yml} 하나이고,
 * 검증용 임시 DB만 시스템 프로퍼티로 덮어쓴다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeedSettingsTest {

    @Test
    void 접속_설정의_원본은_로컬_프로파일_YAML이다() {
        final Map<String, String> previous = SeedSystemProperties.set(Map.of(
                "seed.project-dir", SeedTestPaths.projectDir().toString()));
        try {
            System.clearProperty("seed.jdbc-url");
            final SeedSettings settings = SeedSettings.load();

            final String localProfile = Files.readString(
                    SeedTestPaths.projectDir().resolve("src/main/resources/application-local.yml"));

            assertThat(localProfile)
                    .as("시드가 읽은 URL이 로컬 프로파일에 그대로 적혀 있어야 한다")
                    .contains(settings.jdbcUrl());
            assertThat(settings.jdbcUrl()).startsWith("jdbc:h2:file:");
            assertThat(settings.jdbcUrl())
                    .as("앱이 붙어 있는 파일 DB에 같이 붙으려면 AUTO_SERVER가 켜져 있어야 한다")
                    .contains("AUTO_SERVER=TRUE");
        } catch (final java.io.IOException exception) {
            throw new AssertionError(exception);
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    @Test
    void 기본값은_로컬_프로파일_기준이다() {
        final Map<String, String> previous = SeedSystemProperties.set(Map.of(
                "seed.project-dir", SeedTestPaths.projectDir().toString(),
                "seed.jdbc-url", "jdbc:h2:mem:seed-settings"));
        try {
            System.clearProperty("seed.load-test-members.count");
            System.clearProperty("seed.load-test-fixture.performance-count");
            System.clearProperty("seed.batch-size");

            final SeedSettings settings = SeedSettings.load();

            assertThat(settings.loadTestMemberCount()).isEqualTo(2000);
            assertThat(settings.loadTestPerformanceCount()).isEqualTo(8);
            assertThat(settings.batchSize()).isEqualTo(500);
            assertThat(settings.loadTestMemberPassword()).isEqualTo("password1234");
            assertThat(settings.sqlPath()).endsWith(java.nio.file.Path.of("seed", "sql", "kopis-curated.sql"));
            assertThat(Files.isRegularFile(settings.sqlPath())).isTrue();
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    @Test
    void 검증용_임시_DB와_SQL_경로를_덮어쓸_수_있다() {
        final Map<String, String> previous = SeedSystemProperties.set(Map.of(
                "seed.project-dir", SeedTestPaths.projectDir().toString(),
                "seed.jdbc-url", "jdbc:h2:mem:seed-override",
                "seed.jdbc-username", "tester",
                "seed.sql-path", SeedTestPaths.minimalSeedSql().toString(),
                "seed.load-test-members.count", "7"));
        try {
            final SeedSettings settings = SeedSettings.load();

            assertThat(settings.jdbcUrl()).isEqualTo("jdbc:h2:mem:seed-override");
            assertThat(settings.jdbcUsername()).isEqualTo("tester");
            assertThat(settings.sqlPath()).isEqualTo(SeedTestPaths.minimalSeedSql());
            assertThat(settings.loadTestMemberCount()).isEqualTo(7);
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    @Test
    void 정수가_아닌_설정값은_실행_전에_실패한다() {
        final Map<String, String> previous = SeedSystemProperties.set(Map.of(
                "seed.project-dir", SeedTestPaths.projectDir().toString(),
                "seed.jdbc-url", "jdbc:h2:mem:seed-invalid",
                "seed.load-test-members.count", "여덟"));
        try {
            assertThatThrownBy(SeedSettings::load)
                    .isInstanceOf(SeedFailure.class)
                    .hasMessageContaining("seed.load-test-members.count");
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    @Test
    void 접속_비밀번호는_출력에서_가려진다() {
        assertThat(SeedConsole.maskedUrl("jdbc:h2:file:~/ticket-local;MODE=Oracle;PASSWORD=secret;X=1"))
                .doesNotContain("secret")
                .contains("PASSWORD=***");
        assertThat(SeedConsole.maskedUrl("jdbc:oracle:thin:@host?user=admin&password=secret"))
                .doesNotContain("secret", "admin");
    }
}
