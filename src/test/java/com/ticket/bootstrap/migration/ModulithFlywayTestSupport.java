package com.ticket.bootstrap.migration;

import org.flywaydb.core.Flyway;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.runtime.flyway.MigrationFilter;
import org.springframework.modulith.runtime.flyway.SpringModulithFlywayMigrationStrategy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * {@code spring.modulith.runtime.flyway-enabled=true}가 실제로 등록하는
 * {@link SpringModulithFlywayMigrationStrategy}를 테스트에서 재사용하기 위한 헬퍼다. 운영 코드와
 * 같은 class를 직접 호출해, 테스트가 실제 module-aware Flyway split mechanism과 어긋나지 않게 한다.
 */
public final class ModulithFlywayTestSupport {

    private ModulithFlywayTestSupport() {
    }

    /** {@code __root} migration만(module 없이) 실행한다. */
    public static void migrateRootOnly(final String url) {
        migrate(url, List.of());
    }

    /**
     * {@code __root}와 주어진 module들의 migration을 각각 독립된
     * {@code flyway_schema_history[_module]} table로 실행한다.
     */
    public static void migrate(final String url, final List<String> moduleIdentifiers) {
        final Flyway baseFlyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(
                        "classpath:db/migration",
                        "classpath:db/migration-vendor/h2"
                )
                .load();

        final ApplicationModuleIdentifiers identifiers = ApplicationModuleIdentifiers.of(
                moduleIdentifiers.stream()
                        .map(ApplicationModuleIdentifier::of)
                        .toList());

        new SpringModulithFlywayMigrationStrategy(identifiers, MigrationFilter.USE_ALL).migrate(baseFlyway);
    }

    public static boolean tableExists(final Connection connection, final String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    public static Connection connect(final String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }
}
