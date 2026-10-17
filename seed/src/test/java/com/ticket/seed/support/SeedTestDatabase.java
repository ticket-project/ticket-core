package com.ticket.seed.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 시드 테스트 JVM 하나가 함께 쓰는 PostgreSQL 컨테이너다. 운영(RDS)·로컬과 같은 DB에서 적재 경로를 검증한다.
 *
 * <p>컨테이너는 처음 쓰일 때 한 번 뜨고 JVM이 끝날 때 Testcontainers의 Ryuk이 정리한다. 테스트끼리는 {@link #newSchemaUrl}로 받은 schema로 격리한다. 서비스 테스트의
 * {@code PostgreSqlTestDatabase}와 같은 방식이다 — seed source set은 서비스 테스트 classpath를 보지 않아 따로 둔다. image 태그도
 * {@code TestContainerImages.POSTGRESQL}과 같게 맞춘다.
 */
@SuppressWarnings("resource")
public final class SeedTestDatabase {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    static {
        POSTGRES.start();
    }

    private SeedTestDatabase() {}

    public static String username() {
        return POSTGRES.getUsername();
    }

    public static String password() {
        return POSTGRES.getPassword();
    }

    /** 이름이 {@code prefix}로 시작하는 빈 schema를 새로 만들고, 그 schema를 기본으로 쓰는 JDBC URL을 돌려준다. */
    public static String newSchemaUrl(final String prefix) {
        final String schema =
                prefix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_") + "_" + SCHEMA_SEQUENCE.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), username(), password());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        } catch (final SQLException e) {
            throw new IllegalStateException("테스트 schema를 만들지 못했다: " + schema, e);
        }
        // getJdbcUrl()은 이미 쿼리 문자열(?loggerLevel=OFF)을 갖는다.
        return POSTGRES.getJdbcUrl() + "&currentSchema=" + schema;
    }

    public static DataSource dataSource(final String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, username(), password());
    }

    /** 시드 프로그램이 이 컨테이너에 붙도록 넘기는 {@code seed.jdbc-*} 시스템 프로퍼티다. */
    public static Map<String, String> seedProperties(final String jdbcUrl) {
        return Map.of("seed.jdbc-url", jdbcUrl, "seed.jdbc-username", username(), "seed.jdbc-password", password());
    }
}
