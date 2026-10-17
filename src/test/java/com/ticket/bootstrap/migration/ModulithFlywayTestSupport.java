package com.ticket.bootstrap.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExceptionHandler;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.runtime.flyway.MigrationFilter;
import org.springframework.modulith.runtime.flyway.SpringModulithFlywayMigrationStrategy;

import com.ticket.testsupport.PostgreSqlTestDatabase;

/**
 * {@code spring.modulith.runtime.flyway-enabled=true}가 실제로 등록하는 {@link SpringModulithFlywayMigrationStrategy}를 테스트에서
 * 재사용하기 위한 헬퍼다. 운영 코드와 같은 class를 직접 호출해, 테스트가 실제 module-aware Flyway split mechanism과 어긋나지 않게 한다.
 *
 * <p>접속 대상은 {@link PostgreSqlTestDatabase}의 컨테이너다. 테스트마다 {@link PostgreSqlTestDatabase#newSchemaUrl}로 받은 schema URL을
 * 넘긴다.
 */
public final class ModulithFlywayTestSupport {
    private ModulithFlywayTestSupport() {}

    /** {@code db/migration-vendor/postgresql}의 {@code __root}와 주어진 module migration을 운영과 같은 module-aware 전략으로 적용한다. */
    public static void applyMigrations(final String url, final List<String> moduleIdentifiers) {
        final Flyway baseFlyway = Flyway.configure()
                .dataSource(url, PostgreSqlTestDatabase.username(), PostgreSqlTestDatabase.password())
                .locations("classpath:db/migration-vendor/postgresql")
                .load();

        final ApplicationModuleIdentifiers identifiers = ApplicationModuleIdentifiers.of(
                moduleIdentifiers.stream().map(ApplicationModuleIdentifier::of).toList());

        new SpringModulithFlywayMigrationStrategy(identifiers, MigrationFilter.USE_ALL).migrate(baseFlyway);
    }

    /**
     * Spring Boot와 같은 naming 전략으로 {@code url}에 붙는 Hibernate registry다. 호출자가
     * {@code StandardServiceRegistryBuilder.destroy}로 닫는다.
     */
    public static StandardServiceRegistry hibernateRegistry(final String url) {
        return new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.connection.username", PostgreSqlTestDatabase.username())
                .applySetting("hibernate.connection.password", PostgreSqlTestDatabase.password())
                .applySetting(
                        "hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .applySetting(
                        "hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .build();
    }

    /** 운영의 {@code ddl-auto=validate}와 같은 검증이다. 스키마가 매핑과 다르면 예외를 던지고, 그대로 반환하면 통과다. */
    public static void validateSchema(final StandardServiceRegistry registry, final Metadata metadata) {
        final SchemaManagementTool tool = registry.getService(SchemaManagementTool.class);
        final Map<String, Object> configValues = Map.of();

        final ExecutionOptions options = new ExecutionOptions() {
            @Override
            public Map<String, Object> getConfigurationValues() {
                return configValues;
            }

            @Override
            public boolean shouldManageNamespaces() {
                return true;
            }

            @Override
            public ExceptionHandler getExceptionHandler() {
                return exception -> {
                    throw exception;
                };
            }
        };
        tool.getSchemaValidator(configValues).doValidation(metadata, options, ContributableMatcher.ALL);
    }

    public static Connection connect(final String url) throws SQLException {
        return DriverManager.getConnection(url, PostgreSqlTestDatabase.username(), PostgreSqlTestDatabase.password());
    }
}
