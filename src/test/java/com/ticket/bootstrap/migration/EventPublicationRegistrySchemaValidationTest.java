package com.ticket.bootstrap.migration;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExceptionHandler;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.jpa.archiving.ArchivedJpaEventPublication;
import org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

/**
 * Task 11 Step 2: {@code V8__create_event_publication_registry.sql}(H2)이 실제로 Spring Modulith
 * 2.1.1의 {@link DefaultJpaEventPublication}/{@link ArchivedJpaEventPublication} 매핑과
 * {@code hibernate.hbm2ddl.auto=validate}를 통과하는지 검증한다.
 *
 * <p>이 DDL은 추측이 아니라 Hibernate 7.4.5 + Boot 4.1.1 기본 naming strategy로 schema export를 실제
 * 실행해 캡처한 결과를 옮긴 것이다. 이 테스트는 그 캡처가 실제 마이그레이션 파일과 계속 맞는지 회귀
 * 방지로 고정한다. Oracle DDL은 같은 {@link Metadata}에서 dialect만 바꿔 파생했으므로(타입은 Hibernate가
 * 직접 결정), 라이브 Oracle 없이도 같은 근거를 공유한다 — Oracle 자체 문법 검증은 Step 7에서 다룬다.
 */
class EventPublicationRegistrySchemaValidationTest {

    @Test
    void h2_registry_migration_ddl_validates_against_modulith_2_1_1_mapping() throws Exception {
        final String url = "jdbc:h2:mem:event-publication-registry-validate;MODE=Oracle;DB_CLOSE_DELAY=-1";

        applyMigrationSql(url, Path.of(
                "src/main/resources/db/migration-vendor/h2/__root/V8__create_event_publication_registry.sql"));

        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .build();
        try {
            final Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(DefaultJpaEventPublication.class)
                    .addAnnotatedClass(ArchivedJpaEventPublication.class)
                    .buildMetadata();

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

            // 예외 없이 반환하면 검증 통과다.
            tool.getSchemaValidator(configValues).doValidation(metadata, options, ContributableMatcher.ALL);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void applyMigrationSql(final String url, final Path sqlFile) throws Exception {
        final String sql;
        try {
            sql = Files.readAllLines(sqlFile, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.strip().startsWith("--"))
                    .reduce("", (acc, line) -> acc + line + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            for (String rawStatement : sql.split(";")) {
                final String trimmed = rawStatement.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                statement.execute(trimmed);
            }
        }
    }
}
