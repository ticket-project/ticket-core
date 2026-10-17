package com.ticket.seed;

import java.sql.Driver;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * JDBC URL만 보고 드라이버를 골라 {@link DataSource}를 만든다.
 *
 * <p>드라이버를 이름으로 직접 적재한다. {@code DriverManagerDataSource}의 이름 기반 지연 로딩과 달리, 드라이버가 classpath에 없으면 적재를 시작하기 전에 바로 드러난다 —
 * 드라이버 누락을 "테이블이 없다"는 수백 개 실패로 만나지 않기 위해서다. 시드가 지원하는 DB는 PostgreSQL뿐이다.
 */
final class SeedDataSources {
    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";

    private SeedDataSources() {}

    static DataSource create(final SeedSettings settings) {
        final String driverClassName = driverClassNameFor(settings.jdbcUrl());
        try {
            final Driver driver = (Driver)
                    Class.forName(driverClassName).getDeclaredConstructor().newInstance();
            return new SimpleDriverDataSource(
                    driver, settings.jdbcUrl(), settings.jdbcUsername(), settings.jdbcPassword());
        } catch (final ReflectiveOperationException exception) {
            throw new SeedFailure("""
                    JDBC 드라이버를 초기화할 수 없습니다: %s
                      url=%s
                      -> 시드 실행 classpath(gradle/seed.gradle의 seedMain*)에 드라이버가 있는지 확인하세요.
                    """.formatted(driverClassName, SeedConsole.maskedUrl(settings.jdbcUrl())), exception);
        }
    }

    static String driverClassNameFor(final String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new SeedFailure("JDBC URL이 비어 있습니다.");
        }
        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return POSTGRESQL_DRIVER;
        }
        throw new SeedFailure("""
                지원하지 않는 JDBC URL입니다: %s
                  -> PostgreSQL(jdbc:postgresql:) URL인지 확인하세요.
                """.formatted(SeedConsole.maskedUrl(jdbcUrl)));
    }
}
