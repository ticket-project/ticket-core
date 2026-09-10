package com.ticket.seed.support;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 임시 H2 파일 DB에 <b>실제 애플리케이션 스키마</b>를 만든다.
 *
 * <p>시드 테스트가 손으로 쓴 DDL을 쓰면 앱의 매핑이 바뀌어도 통과한다 — 그 순간 테스트는 "시드가
 * 실제 앱 DB에서 동작한다"를 더 이상 보장하지 않는다. 여기서는 로컬 프로파일과 같은 방식
 * (Hibernate {@code ddl-auto: create})으로 앱 entity 매핑에서 스키마를 직접 생성한다.
 *
 * <p>DataSource와 Hibernate JPA auto-configuration <b>둘만</b> 올린다. 웹 서버·Redis·OAuth·Modulith
 * event registry는 올리지 않는다 — 시드 검증에 필요하지 않고, 시드 때문에 그런 인프라가 필요해지는
 * 구조를 만들지 않기 위해서다. 스키마를 만든 뒤 컨텍스트는 곧바로 닫는다(파일 DB라 스키마는
 * 디스크에 남는다).
 */
public final class AppSchema {

    /** 로컬 프로파일과 같은 H2 Oracle 모드다. 시드 SQL이 {@code FROM dual}을 쓴다. */
    private static final String URL_OPTIONS = ";MODE=Oracle;DB_CLOSE_DELAY=-1";

    private AppSchema() {
    }

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
        try (ConfigurableApplicationContext context = context(jdbcUrl, "create")) {
            // 컨텍스트가 뜨는 것만으로 스키마가 만들어진다.
            context.getBeanFactory();
        }
    }

    /**
     * 이미 만들어진 스키마에 붙는 JPA 컨텍스트를 연다. 시드가 넣은 row를 실제 entity 매핑으로
     * 읽어 볼 때 쓴다. 호출자가 닫는다.
     */
    public static ConfigurableApplicationContext openContext(final String jdbcUrl) {
        return context(jdbcUrl, "none");
    }

    private static ConfigurableApplicationContext context(final String jdbcUrl, final String ddlAuto) {
        return new SpringApplicationBuilder(SchemaConfiguration.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.jpa.hibernate.ddl-auto=" + ddlAuto,
                        "spring.jpa.open-in-view=false",
                        "logging.level.root=WARN")
                .run();
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EntityScan("com.ticket")
    static class SchemaConfiguration {
    }
}
