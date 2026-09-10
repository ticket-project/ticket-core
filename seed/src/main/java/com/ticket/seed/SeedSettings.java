package com.ticket.seed;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 시드 실행 설정이다.
 *
 * <p><b>접속 설정의 원본은 {@code src/main/resources/application-local.yml} 하나다.</b> 앱과 시드가
 * 같은 로컬 H2 DB를 보게 하려면 URL을 두 곳에 적어 둘 수 없다 — 여기서는 그 파일의
 * {@code spring.datasource.*}를 직접 읽는다. 검증용으로 임시 H2를 쓸 때만
 * {@code -Dseed.jdbc-url}(그리고 필요하면 {@code -Dseed.jdbc-username} /
 * {@code -Dseed.jdbc-password})로 덮어쓴다.
 *
 * <p>회원 비밀번호는 {@code SEED_LOAD_TEST_MEMBER_PASSWORD} 환경변수로 넘긴다. 기본값
 * {@code password1234}는 형제 저장소 {@code gatling-test}의 {@code loginPassword} 기본값과 같은
 * 로컬 전용 값이며, 운영 자격증명이 아니다.
 */
record SeedSettings(
        String jdbcUrl,
        String jdbcUsername,
        String jdbcPassword,
        Path sqlPath,
        int batchSize,
        int loadTestMemberCount,
        String loadTestMemberPassword,
        int loadTestPerformanceCount
) {

    /** 로컬 프로파일의 회원 2,000명·부하 테스트 회차 8개를 기본 동작으로 삼는다. */
    static final int DEFAULT_LOAD_TEST_MEMBER_COUNT = 2000;
    static final int DEFAULT_LOAD_TEST_PERFORMANCE_COUNT = 8;
    static final int DEFAULT_BATCH_SIZE = 500;
    static final String DEFAULT_LOAD_TEST_MEMBER_PASSWORD = "password1234";

    private static final String LOCAL_PROFILE_YAML = "src/main/resources/application-local.yml";
    private static final String SEED_SQL_RELATIVE_PATH = "seed/sql/kopis-curated.sql";

    static SeedSettings load() {
        final Path projectDir = projectDir();
        final Datasource datasource = resolveDatasource(projectDir);

        return new SeedSettings(
                datasource.url(),
                datasource.username(),
                datasource.password(),
                resolveSqlPath(projectDir),
                intProperty("seed.batch-size", DEFAULT_BATCH_SIZE),
                intProperty("seed.load-test-members.count", DEFAULT_LOAD_TEST_MEMBER_COUNT),
                memberPassword(),
                intProperty("seed.load-test-fixture.performance-count", DEFAULT_LOAD_TEST_PERFORMANCE_COUNT)
        );
    }

    private static Path projectDir() {
        final String configured = System.getProperty("seed.project-dir");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path resolveSqlPath(final Path projectDir) {
        final String configured = System.getProperty("seed.sql-path");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return projectDir.resolve(SEED_SQL_RELATIVE_PATH).normalize();
    }

    private static Datasource resolveDatasource(final Path projectDir) {
        final String overriddenUrl = System.getProperty("seed.jdbc-url");
        if (overriddenUrl != null && !overriddenUrl.isBlank()) {
            return new Datasource(
                    overriddenUrl,
                    System.getProperty("seed.jdbc-username", "sa"),
                    System.getProperty("seed.jdbc-password", "")
            );
        }
        return readLocalProfileDatasource(projectDir.resolve(LOCAL_PROFILE_YAML));
    }

    private static Datasource readLocalProfileDatasource(final Path yamlPath) {
        if (!Files.isRegularFile(yamlPath)) {
            throw new SeedFailure(
                    "로컬 접속 설정을 읽을 수 없습니다. 파일이 없습니다: " + yamlPath
                            + System.lineSeparator()
                            + "  -> ticket 저장소 루트에서 실행했는지 확인하거나 -Dseed.jdbc-url로 직접 지정하세요.");
        }

        final Map<String, Object> yaml = loadYaml(yamlPath);
        final Object url = nested(yaml, "spring", "datasource", "url");
        if (url == null) {
            throw new SeedFailure(
                    "로컬 접속 설정에 spring.datasource.url이 없습니다: " + yamlPath);
        }

        final Object username = nested(yaml, "spring", "datasource", "username");
        final Object password = nested(yaml, "spring", "datasource", "password");
        return new Datasource(
                url.toString(),
                username == null ? "sa" : username.toString(),
                password == null ? "" : password.toString()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(final Path yamlPath) {
        try (InputStream input = Files.newInputStream(yamlPath)) {
            final Object loaded = new Yaml().load(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            throw new SeedFailure("로컬 접속 설정의 형식이 map이 아닙니다: " + yamlPath);
        } catch (final IOException exception) {
            throw new SeedFailure("로컬 접속 설정을 읽을 수 없습니다: " + yamlPath, exception);
        }
    }

    private static Object nested(final Map<String, Object> root, final String... keys) {
        Object current = root;
        for (final String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    private static String memberPassword() {
        final String fromEnvironment = System.getenv("SEED_LOAD_TEST_MEMBER_PASSWORD");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        return DEFAULT_LOAD_TEST_MEMBER_PASSWORD;
    }

    private static int intProperty(final String name, final int defaultValue) {
        final String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException exception) {
            throw new SeedFailure("-D" + name + " 값이 정수가 아닙니다: " + raw, exception);
        }
    }

    private record Datasource(String url, String username, String password) {
    }
}
