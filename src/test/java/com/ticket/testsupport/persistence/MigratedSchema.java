package com.ticket.testsupport.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.runtime.flyway.MigrationFilter;
import org.springframework.modulith.runtime.flyway.SpringModulithFlywayMigrationStrategy;
import org.springframework.test.context.TestPropertySource;

/**
 * 테스트 H2의 스키마를 entity가 아니라 <b>운영과 같은 Flyway migration</b>으로 만든다.
 *
 * <p>스키마의 원본은 {@code db/migration}이다. {@code ddl-auto=create}로 entity에서 만든 스키마에는 migration에만 있는 유니크 제약·인덱스가 없어서 운영과 다르다
 * — 중복 거절 같은 DB 동작을 테스트할 수 없다.
 *
 * <p>context가 뜰 때마다 H2를 비운 뒤 모든 module migration을 운영 순서로 적용한다. {@code create-drop}처럼 context마다 새 스키마에서 시작한다. H2 전용이다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Import(MigratedSchema.Migration.class)
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.database-platform=com.ticket.shared.config.H2OracleModeDialect",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration,classpath:db/migration-vendor/h2",
            // 슬라이스 context에는 main class가 없어 Modulith가 module 목록을 만들지 못한다. 아래 전략이 목록을 직접 준다.
            "spring.modulith.runtime.flyway-enabled=false"
        })
public @interface MigratedSchema {
    /** 운영 기동 순서와 같다(jar의 application-modules.json). */
    List<String> MODULES_IN_RUNTIME_ORDER =
            List.of("shared", "member", "payment", "venue", "like", "security", "show", "booking");

    @TestConfiguration(proxyBeanMethods = false)
    class Migration {
        @Bean
        FlywayMigrationStrategy migratedSchemaStrategy() {
            final ApplicationModuleIdentifiers modules =
                    ApplicationModuleIdentifiers.of(MODULES_IN_RUNTIME_ORDER.stream()
                            .map(ApplicationModuleIdentifier::of)
                            .toList());
            return flyway -> {
                final DataSource dataSource = flyway.getConfiguration().getDataSource();
                new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
                new SpringModulithFlywayMigrationStrategy(modules, MigrationFilter.USE_ALL).migrate(flyway);
            };
        }
    }
}
