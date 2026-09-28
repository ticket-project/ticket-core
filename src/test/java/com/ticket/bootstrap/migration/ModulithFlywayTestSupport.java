package com.ticket.bootstrap.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.runtime.flyway.MigrationFilter;
import org.springframework.modulith.runtime.flyway.SpringModulithFlywayMigrationStrategy;

/**
 * {@code spring.modulith.runtime.flyway-enabled=true}가 실제로 등록하는 {@link SpringModulithFlywayMigrationStrategy}를 테스트에서
 * 재사용하기 위한 헬퍼다. 운영 코드와 같은 class를 직접 호출해, 테스트가 실제 module-aware Flyway split mechanism과 어긋나지 않게 한다.
 */
public final class ModulithFlywayTestSupport {
    private ModulithFlywayTestSupport() {}

    /** {@code __root} migration만(module 없이) 실행한다. */
    public static void migrateRootOnly(final String url) {
        migrate(url, List.of());
    }

    /**
     * 테스트가 직접 만든 옛 schema 위에 {@code __root}와 주어진 module들의 migration을 운영처럼 적용한다.
     *
     * <p>운영 DB는 Flyway 도입 전부터 테이블이 있었고, {@code flyway_schema_history}에 version {@code 1} BASELINE이 먼저 기록돼 있다. 그래서
     * pre-Flyway schema를 다시 만드는 {@code __root} V1은 운영에서 건너뛴다. 그 기록을 똑같이 남겨야 V1이 테스트가 만든 테이블과 부딪히지 않는다.
     */
    public static void migrate(final String url, final List<String> moduleIdentifiers) {
        baselineRootLikeProd(url, "sa", "");
        migrateFromEmpty(url, moduleIdentifiers);
    }

    /** 빈 DB에 {@code __root} V1부터 migration을 적용한다. 로컬 H2가 처음 만들어질 때와 같다. */
    public static void migrateFromEmpty(final String url, final List<String> moduleIdentifiers) {
        final Flyway baseFlyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration", "classpath:db/migration-vendor/h2")
                .load();

        final ApplicationModuleIdentifiers identifiers = ApplicationModuleIdentifiers.of(
                moduleIdentifiers.stream().map(ApplicationModuleIdentifier::of).toList());

        new SpringModulithFlywayMigrationStrategy(identifiers, MigrationFilter.USE_ALL).migrate(baseFlyway);
    }

    /** 운영의 {@code __root} 이력처럼 version {@code 1} BASELINE을 기록한다. 이력이 이미 있으면(같은 DB에 두 번째 migrate) 그대로 둔다. */
    public static void baselineRootLikeProd(final String url, final String username, final String password) {
        final Flyway rootFlyway = Flyway.configure()
                .dataSource(url, username, password)
                // module 폴더까지 읽으면 버전이 겹친다. 이력만 볼 것이라 __root 공통 폴더만 둔다.
                .locations("classpath:db/migration/__root")
                .baselineVersion("1")
                .baselineDescription("existing schema before Flyway")
                .load();
        if (rootFlyway.info().current() == null) {
            rootFlyway.baseline();
        }
    }

    public static boolean tableExists(final Connection connection, final String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    public static Connection connect(final String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }
}
