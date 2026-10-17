package com.ticket.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트 JVM 하나가 함께 쓰는 PostgreSQL 컨테이너다. 운영(RDS)과 같은 DB에서 migration·매핑·제약을 검증한다.
 *
 * <p>컨테이너는 처음 쓰일 때 한 번 뜨고 JVM이 끝날 때 Testcontainers의 Ryuk이 정리한다. 테스트 클래스마다 띄우면 클래스 사이에 재사용되는 Spring context가 이미 멈춘 컨테이너를
 * 가리킨다({@link CoreApplicationTestSupport} 참고).
 *
 * <p>서로 격리가 필요한 테스트는 {@link #newSchemaUrl}로 자기 schema를 받는다. 전체 애플리케이션 테스트는 기본 schema({@code public})를 쓴다.
 */
@SuppressWarnings("resource")
public final class PostgreSqlTestDatabase {
    /**
     * 캐시된 Spring context마다 커넥션 풀이 남는다. 기본 {@code max_connections}(100)로는 context가 많을 때 접속이 거절된다. {@code fsync=off}는
     * Testcontainers 기본 명령에 있던 값을 그대로 둔다.
     */
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestContainerImages.POSTGRESQL)
            .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=500");

    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    static {
        POSTGRES.start();
    }

    private PostgreSqlTestDatabase() {}

    public static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    public static String username() {
        return POSTGRES.getUsername();
    }

    public static String password() {
        return POSTGRES.getPassword();
    }

    /** 이름이 {@code prefix}로 시작하는 빈 schema를 새로 만들고, 그 schema를 기본으로 쓰는 JDBC URL을 돌려준다. */
    public static String newSchemaUrl(final String prefix) {
        final String schema = prefix + "_" + SCHEMA_SEQUENCE.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        } catch (final SQLException e) {
            throw new IllegalStateException("테스트 schema를 만들지 못했다: " + schema, e);
        }
        // getJdbcUrl()은 이미 쿼리 문자열(?loggerLevel=OFF)을 갖는다.
        return jdbcUrl() + "&currentSchema=" + schema;
    }
}
