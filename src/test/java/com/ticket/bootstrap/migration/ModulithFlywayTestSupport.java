package com.ticket.bootstrap.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        applyMigrations(url, "sa", "", "h2", moduleIdentifiers);
    }

    /**
     * {@code db/migration}과 {@code db/migration-vendor/{vendor}}의 {@code __root}와 주어진 module migration을 운영과 같은
     * module-aware 전략으로 적용한다. {@code vendor}는 {@code h2} 또는 {@code oracle}이다.
     */
    public static void applyMigrations(
            final String url,
            final String username,
            final String password,
            final String vendor,
            final List<String> moduleIdentifiers) {
        final Flyway baseFlyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration", "classpath:db/migration-vendor/" + vendor)
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

    /** 이름이 {@code name}인 Oracle 모드 H2 in-memory DB의 URL이다. 연결이 모두 닫혀도 DB는 JVM이 끝날 때까지 남는다. */
    public static String h2Url(final String name) {
        return "jdbc:h2:mem:" + name + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    /** {@code tableName}에 걸린 외래 키 이름들이다. cross-module FK 제거 migration을 확인할 때 쓴다. */
    public static Set<String> foreignKeyNames(final Connection connection, final String tableName) throws SQLException {
        final Set<String> names = new HashSet<>();
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, tableName)) {
            while (keys.next()) {
                names.add(keys.getString("FK_NAME"));
            }
        }
        return names;
    }

    /**
     * Spring Boot와 같은 naming 전략으로 {@code url}(H2, sa)에 붙는 Hibernate registry다. 호출자가
     * {@code StandardServiceRegistryBuilder.destroy}로 닫는다.
     */
    public static StandardServiceRegistry hibernateRegistry(final String url) {
        return new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
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

    /**
     * booking slicing 테스트가 쓰는 pre-Flyway baseline이다. PERFORMANCE_SEATS/PERFORMANCES/SEATS/ORDER_SEATS는 어떤 Flyway
     * migration도 만들지 않는다(V1은 운영에서 BASELINE으로 건너뛴다, docs/operations.md 참고). booking의 FK 제거 migration을 검증할 수 있도록 옛
     * cross-module FK까지 재현한다.
     */
    public static void createBookingLegacyBaseline(final String url) throws SQLException {
        try (Connection connection = connect(url);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE seats (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE performance_seats ("
                    + "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                    + "  performance_id BIGINT NOT NULL, "
                    + "  seat_id BIGINT NOT NULL, "
                    + "  state VARCHAR(255), "
                    + "  price DECIMAL(38, 2), "
                    + "  created_at TIMESTAMP NOT NULL, "
                    + "  created_by VARCHAR(255) NOT NULL, "
                    + "  updated_at TIMESTAMP, "
                    + "  updated_by VARCHAR(255), "
                    + "  CONSTRAINT legacy_fk_performance FOREIGN KEY (performance_id) REFERENCES performances, "
                    + "  CONSTRAINT legacy_fk_seat FOREIGN KEY (seat_id) REFERENCES seats"
                    + ")");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
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
