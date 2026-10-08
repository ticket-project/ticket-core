package com.ticket.seed.support;

import java.nio.file.Path;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.runtime.flyway.MigrationFilter;
import org.springframework.modulith.runtime.flyway.SpringModulithFlywayMigrationStrategy;

/**
 * 임시 H2 파일 DB(또는 Oracle Testcontainer)에 <b>운영과 같은 Flyway migration</b>으로 애플리케이션 스키마를 만든다.
 *
 * <p>시드 테스트가 손으로 쓴 DDL이나 entity 매핑({@code ddl-auto: create})으로 스키마를 만들면 실제 DB와 어긋난다. 시드 SQL은 컬럼을 직접 INSERT하는데, entity가
 * 읽지 않아 매핑을 지운 컬럼(예: {@code VENUES.address_detail})은 entity로 만든 스키마에 없어서 적재가 실패한다. 스키마의 원본은 migration이다(ADR 0020) — 유니크
 * 제약·인덱스·{@code DEFAULT}도 여기서 운영과 같아진다.
 *
 * <p>module별 이력을 나누는 {@link SpringModulithFlywayMigrationStrategy}를 운영과 같은 방식으로 직접 호출한다. Spring context는 migration에 쓰지
 * 않는다. 스키마를 만든 뒤 파일 DB라 스키마는 디스크에 남는다.
 *
 * <p>{@link #openContext}는 이미 만들어진 스키마에 붙는 JPA 컨텍스트다. DataSource와 Hibernate JPA auto-configuration <b>둘만</b> 올린다. 웹
 * 서버·Redis·OAuth·Modulith event registry는 올리지 않는다 — 시드 검증에 필요하지 않고, 시드 때문에 그런 인프라가 필요해지는 구조를 만들지 않기 위해서다.
 */
public final class AppSchema {
    /** 로컬 프로파일과 같은 H2 Oracle 모드다. 시드 SQL이 {@code FROM dual}을 쓴다. */
    private static final String URL_OPTIONS = ";MODE=Oracle;DB_CLOSE_DELAY=-1";

    /**
     * 운영 기동 순서와 같다(jar의 application-modules.json). 서비스 테스트의 {@code MigratedSchema.MODULES_IN_RUNTIME_ORDER}와 같은 목록이다.
     */
    private static final List<String> MODULES_IN_RUNTIME_ORDER =
            List.of("shared", "member", "payment", "venue", "like", "security", "show", "booking");

    private AppSchema() {}

    /** {@code directory} 아래에 이름이 {@code name}인 H2 파일 DB를 만들고 JDBC URL을 돌려준다. */
    public static String createIn(final Path directory, final String name) {
        return createIn(directory, name, "");
    }

    /** {@code extraUrlOptions}로 {@code ;AUTO_SERVER=TRUE} 같은 옵션을 덧붙인다. */
    public static String createIn(final Path directory, final String name, final String extraUrlOptions) {
        final String jdbcUrl = urlFor(directory, name) + extraUrlOptions;
        create(jdbcUrl);
        return jdbcUrl;
    }

    /** 스키마를 만들지 않은 JDBC URL이다. 준비되지 않은 DB를 테스트할 때 쓴다. */
    public static String urlFor(final Path directory, final String name) {
        final String absolutePath = directory.resolve(name).toAbsolutePath().toString();
        return "jdbc:h2:file:" + absolutePath.replace('\\', '/') + URL_OPTIONS;
    }

    public static void create(final String jdbcUrl) {
        migrate(jdbcUrl, "sa", "", "h2");
    }

    /**
     * 운영 검증용 Oracle Testcontainer에 Oracle migration으로 스키마를 만든다.
     *
     * <p><b>테스트 소스에만 둔다.</b> 이 기능을 시드 프로그램에 노출하면 "운영 DB 초기화" 명령이 되어 버린다 — 테이블 생성·삭제는 이 도구의 범위가 아니고, {@code seedProd}는
     * 이미 준비된 테이블에 데이터만 넣는다.
     */
    public static void createOn(final String jdbcUrl, final String username, final String password) {
        migrate(jdbcUrl, username, password, jdbcUrl.startsWith("jdbc:postgresql:") ? "postgresql" : "oracle");
    }

    /** {@code vendor}는 {@code h2} 또는 {@code oracle}이다. 로컬 프로파일·운영과 같은 locations를 쓴다. */
    private static void migrate(
            final String jdbcUrl, final String username, final String password, final String vendor) {
        final String[] locations = vendor.equals("postgresql")
                ? new String[] {"classpath:db/migration-vendor/postgresql"}
                : new String[] {"classpath:db/migration", "classpath:db/migration-vendor/" + vendor};
        final Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations(locations)
                .load();
        final ApplicationModuleIdentifiers modules = ApplicationModuleIdentifiers.of(MODULES_IN_RUNTIME_ORDER.stream()
                .map(ApplicationModuleIdentifier::of)
                .toList());
        new SpringModulithFlywayMigrationStrategy(modules, MigrationFilter.USE_ALL).migrate(flyway);
    }

    /** 이미 만들어진 스키마에 붙는 JPA 컨텍스트를 연다. 시드가 넣은 row를 실제 entity 매핑으로 읽어 볼 때 쓴다. 호출자가 닫는다. */
    public static ConfigurableApplicationContext openContext(final String jdbcUrl) {
        return context(jdbcUrl);
    }

    public static ConfigurableApplicationContext openContext(
            final String jdbcUrl, final String username, final String password) {
        return new SpringApplicationBuilder(SchemaConfiguration.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.open-in-view=false",
                        "logging.level.root=WARN")
                .run();
    }

    private static ConfigurableApplicationContext context(final String jdbcUrl) {
        return new SpringApplicationBuilder(SchemaConfiguration.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.driver-class-name=" + driverClassName(jdbcUrl),
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "spring.jpa.open-in-view=false",
                        "logging.level.root=WARN")
                .run();
    }

    private static String driverClassName(final String jdbcUrl) {
        return jdbcUrl.startsWith("jdbc:oracle:") ? "oracle.jdbc.OracleDriver" : "org.h2.Driver";
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EntityScan("com.ticket")
    static class SchemaConfiguration {}
}
