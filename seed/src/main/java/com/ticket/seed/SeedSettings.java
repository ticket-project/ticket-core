package com.ticket.seed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * 시드 실행 설정이다. 접속 설정의 원본은 {@link SeedTarget}이 정한다.
 *
 * <p><b>로컬의 원본은 {@code src/main/resources/application-local.yml} 하나다.</b> 앱과 시드가 같은 로컬 H2 DB를 보게
 * 하려면 URL을 두 곳에 적어 둘 수 없다 — 여기서는 그 파일의 {@code spring.datasource.*}를 직접 읽는다.
 *
 * <p><b>운영의 원본은 환경변수뿐이다.</b> {@code SPRING_DATASOURCE_URL} / {@code SPRING_DATASOURCE_USERNAME} /
 * {@code SPRING_DATASOURCE_PASSWORD}가 하나라도 없으면 적재를 시작하지 않고 실패한다 — 로컬 프로파일 YAML로 대체하지 않는다. 운영 DB로
 * 넣으려던 데이터가 조용히 로컬 H2에 들어가는 것이 이 프로그램에서 가장 나쁜 결과다.
 *
 * <p>검증용으로 임시 DB를 쓸 때만 {@code -Dseed.jdbc-url}(그리고 필요하면 {@code -Dseed.jdbc-username} / {@code
 * -Dseed.jdbc-password})로 덮어쓴다. 두 대상 모두에서 이 프로퍼티가 가장 우선이다 — 명시적으로 준 값이기 때문이다.
 *
 * <p>회원 비밀번호는 {@code SEED_LOAD_TEST_MEMBER_PASSWORD} 환경변수로 넘긴다. 로컬 기본값 {@code password1234}는 형제 저장소
 * {@code gatling-test}의 {@code loginPassword} 기본값과 같은 로컬 전용 값이며, 운영 자격증명이 아니다. <b>운영에 테스트 회원을 만들 때는
 * 기본값을 쓰지 않고 반드시 이 환경변수를 명시해야 한다.</b>
 */
record SeedSettings(
        SeedTarget target,
        String jdbcUrl,
        String jdbcUsername,
        String jdbcPassword,
        Path sqlPath,
        int batchSize,
        int loadTestMemberCount,
        String loadTestMemberPassword,
        int loadTestPerformanceCount) {
    /** 로컬 프로파일의 회원 2,000명·부하 테스트 회차 8개를 기본 동작으로 삼는다. */
    static final int DEFAULT_LOAD_TEST_MEMBER_COUNT = 2000;

    static final int DEFAULT_LOAD_TEST_PERFORMANCE_COUNT = 8;

    /** 운영 기본 적재 대상은 공연 데이터뿐이다. 테스트 회원과 부하 테스트 공연은 옵션을 명시할 때만 만든다. */
    static final int PROD_DEFAULT_LOAD_TEST_MEMBER_COUNT = 0;

    static final int PROD_DEFAULT_LOAD_TEST_PERFORMANCE_COUNT = 0;
    static final int DEFAULT_BATCH_SIZE = 500;
    static final String DEFAULT_LOAD_TEST_MEMBER_PASSWORD = "password1234";
    static final String DATASOURCE_URL_ENV = "SPRING_DATASOURCE_URL";
    static final String DATASOURCE_USERNAME_ENV = "SPRING_DATASOURCE_USERNAME";
    static final String DATASOURCE_PASSWORD_ENV = "SPRING_DATASOURCE_PASSWORD";
    static final String MEMBER_PASSWORD_ENV = "SEED_LOAD_TEST_MEMBER_PASSWORD";
    private static final String LOCAL_PROFILE_YAML = "src/main/resources/application-local.yml";
    private static final String SEED_SQL_RELATIVE_PATH = "seed/sql/kopis-curated.sql";

    /** 로컬 설정이다. 기존 {@code seedLocal} 동작과 기본값을 그대로 유지한다. */
    static SeedSettings load() {
        return forLocal(System.getenv());
    }

    static SeedSettings forLocal(final Map<String, String> environment) {
        final Path projectDir = projectDir();
        final Datasource datasource = resolveLocalDatasource(projectDir);
        final int memberCount =
                intProperty("seed.load-test-members.count", DEFAULT_LOAD_TEST_MEMBER_COUNT);

        return new SeedSettings(
                SeedTarget.LOCAL,
                datasource.url(),
                datasource.username(),
                datasource.password(),
                resolveSqlPath(projectDir),
                intProperty("seed.batch-size", DEFAULT_BATCH_SIZE),
                memberCount,
                localMemberPassword(environment),
                intProperty(
                        "seed.load-test-fixture.performance-count",
                        DEFAULT_LOAD_TEST_PERFORMANCE_COUNT));
    }

    /** 운영 설정이다. 접속 정보는 환경변수에서만 온다. */
    static SeedSettings forProd(final Map<String, String> environment) {
        final Path projectDir = projectDir();
        final Datasource datasource = resolveProdDatasource(environment);
        final int memberCount =
                intProperty("seed.load-test-members.count", PROD_DEFAULT_LOAD_TEST_MEMBER_COUNT);

        return new SeedSettings(
                SeedTarget.PROD,
                datasource.url(),
                datasource.username(),
                datasource.password(),
                resolveSqlPath(projectDir),
                intProperty("seed.batch-size", DEFAULT_BATCH_SIZE),
                memberCount,
                prodMemberPassword(environment, memberCount),
                intProperty(
                        "seed.load-test-fixture.performance-count",
                        PROD_DEFAULT_LOAD_TEST_PERFORMANCE_COUNT));
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

    private static Datasource resolveLocalDatasource(final Path projectDir) {
        final String overriddenUrl = System.getProperty("seed.jdbc-url");
        if (overriddenUrl != null && !overriddenUrl.isBlank()) {
            return new Datasource(
                    overriddenUrl,
                    System.getProperty("seed.jdbc-username", "sa"),
                    System.getProperty("seed.jdbc-password", ""));
        }
        return readLocalProfileDatasource(projectDir.resolve(LOCAL_PROFILE_YAML));
    }

    /**
     * 운영 접속 설정이다. 세 값이 모두 있어야 하고, 하나라도 비어 있으면 <b>여기서 실패한다.</b> 로컬 프로파일 YAML은 쳐다보지도 않는다 — 운영 적재가 조용히
     * 로컬 DB로 흘러가는 경로를 만들지 않는다.
     */
    private static Datasource resolveProdDatasource(final Map<String, String> environment) {
        final String url = propertyOrEnvironment("seed.jdbc-url", DATASOURCE_URL_ENV, environment);
        final String username =
                propertyOrEnvironment("seed.jdbc-username", DATASOURCE_USERNAME_ENV, environment);
        final String password =
                propertyOrEnvironment("seed.jdbc-password", DATASOURCE_PASSWORD_ENV, environment);

        final StringBuilder missing = new StringBuilder();
        if (url == null) {
            missing.append(DATASOURCE_URL_ENV).append(' ');
        }
        if (username == null) {
            missing.append(DATASOURCE_USERNAME_ENV).append(' ');
        }
        if (password == null) {
            missing.append(DATASOURCE_PASSWORD_ENV).append(' ');
        }
        if (!missing.isEmpty()) {
            throw new SeedFailure(
                    """
                    운영 DB 접속 설정이 없습니다. 로컬 DB로 대체하지 않고 적재를 시작하기 전에 중단합니다.
                      없는 환경변수: %s
                      -> 세 값을 모두 설정한 뒤 다시 실행하세요. IntelliJ Gradle 실행 설정에 넣는 방법은
                         seed/README.md를 보세요.
                    """
                            .formatted(missing.toString().trim()));
        }

        return new Datasource(url, username, password);
    }

    private static Datasource readLocalProfileDatasource(final Path yamlPath) {
        if (!Files.isRegularFile(yamlPath)) {
            throw new SeedFailure(
                    "로컬 접속 설정을 읽을 수 없습니다. 파일이 없습니다: "
                            + yamlPath
                            + System.lineSeparator()
                            + "  -> ticket 저장소 루트에서 실행했는지 확인하거나 -Dseed.jdbc-url로 직접 지정하세요.");
        }

        final Map<String, Object> yaml = loadYaml(yamlPath);
        final Object url = nested(yaml, "spring", "datasource", "url");
        if (url == null) {
            throw new SeedFailure("로컬 접속 설정에 spring.datasource.url이 없습니다: " + yamlPath);
        }

        final Object username = nested(yaml, "spring", "datasource", "username");
        final Object password = nested(yaml, "spring", "datasource", "password");
        return new Datasource(
                url.toString(),
                username == null ? "sa" : username.toString(),
                password == null ? "" : password.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(final Path yamlPath) {
        try (InputStream input = Files.newInputStream(yamlPath)) {
            final Object loaded =
                    new Yaml().load(new String(input.readAllBytes(), StandardCharsets.UTF_8));
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

    private static String localMemberPassword(final Map<String, String> environment) {
        final String configured =
                propertyOrEnvironment(
                        "seed.load-test-member-password", MEMBER_PASSWORD_ENV, environment);
        return configured == null ? DEFAULT_LOAD_TEST_MEMBER_PASSWORD : configured;
    }

    /**
     * 운영에 테스트 회원을 만들 때는 기본값을 쓰지 않는다. 저장소에 적힌 값으로 운영 계정이 만들어지면 그 계정은 사실상 공개 계정이다.
     *
     * <p>회원을 만들지 않는 기본 실행({@code count=0})에서는 비밀번호가 필요 없으므로 요구하지 않는다.
     */
    private static String prodMemberPassword(
            final Map<String, String> environment, final int memberCount) {
        final String configured =
                propertyOrEnvironment(
                        "seed.load-test-member-password", MEMBER_PASSWORD_ENV, environment);
        if (memberCount <= 0) {
            return configured == null ? "" : configured;
        }
        if (configured == null) {
            throw new SeedFailure(
                    """
                    운영에 테스트 회원을 만들려면 %s를 명시해야 합니다.
                      -> 저장소에 적힌 기본 비밀번호는 로컬 전용입니다. 운영에서는 쓰지 않습니다.
                         회원을 만들지 않으려면 -Dseed.load-test-members.count=0(운영 기본값)으로 두세요.
                    """
                            .formatted(MEMBER_PASSWORD_ENV));
        }
        return configured;
    }

    /** 명시적으로 준 시스템 프로퍼티가 환경변수보다 우선한다. 둘 다 없거나 공백이면 {@code null}이다. */
    private static String propertyOrEnvironment(
            final String propertyName,
            final String environmentName,
            final Map<String, String> environment) {
        final String fromProperty = System.getProperty(propertyName);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        final String fromEnvironment = environment.get(environmentName);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        return null;
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

    private record Datasource(String url, String username, String password) {}
}
