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
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.runtime.flyway.MigrationFilter;
import org.springframework.modulith.runtime.flyway.SpringModulithFlywayMigrationStrategy;
import org.springframework.test.context.TestPropertySource;

import com.ticket.testsupport.PostgreSqlTestDatabase;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 테스트 PostgreSQL의 스키마를 entity가 아니라 <b>운영과 같은 Flyway migration</b>으로 만든다.
 *
 * <p>스키마의 원본은 {@code db/migration-vendor/postgresql}이다. {@code ddl-auto=create}로 entity에서 만든 스키마에는 migration에만 있는 유니크
 * 제약·인덱스가 없어서 운영과 다르다 — 중복 거절 같은 DB 동작을 테스트할 수 없다.
 *
 * <p>datasource도 이 애노테이션이 정한다. context마다 {@link PostgreSqlTestDatabase}의 컨테이너에 새 schema를 만들고 그 schema를 기본으로 쓰는
 * {@link DataSource} bean을 올린다. 자동 설정 DataSource 대신 쓰이므로 붙이는 테스트는 datasource를 따로 적지 않는다.
 *
 * <p>context마다 빈 schema에 모든 module migration을 운영 순서로 적용한다. {@code create-drop}처럼 context마다 새 스키마에서 시작하고, 캐시된 다른
 * context의 스키마와 섞이지 않는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Import(MigratedSchema.Migration.class)
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration-vendor/postgresql",
            // 슬라이스 context에는 main class가 없어 Modulith가 module 목록을 만들지 못한다. 아래 전략이 목록을 직접 준다.
            "spring.modulith.runtime.flyway-enabled=false"
        })
public @interface MigratedSchema {
    /** 운영 기동 순서와 같다(jar의 application-modules.json). {@code MigratedSchemaTest}가 Modulith의 순서와 대조한다. */
    List<String> MODULES_IN_RUNTIME_ORDER =
            List.of("shared", "member", "payment", "venue", "like", "security", "show", "booking");

    @TestConfiguration(proxyBeanMethods = false)
    class Migration {
        @Bean
        DataSource migratedSchemaDataSource() {
            final HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(PostgreSqlTestDatabase.newSchemaUrl("migrated"));
            dataSource.setUsername(PostgreSqlTestDatabase.username());
            dataSource.setPassword(PostgreSqlTestDatabase.password());
            // 캐시된 context마다 풀이 남는다. 테스트는 동시 접속이 적어 작게 둔다.
            dataSource.setMaximumPoolSize(4);
            return dataSource;
        }

        @Bean
        FlywayMigrationStrategy migratedSchemaStrategy() {
            final ApplicationModuleIdentifiers modules =
                    ApplicationModuleIdentifiers.of(MODULES_IN_RUNTIME_ORDER.stream()
                            .map(ApplicationModuleIdentifier::of)
                            .toList());
            return flyway ->
                    new SpringModulithFlywayMigrationStrategy(modules, MigrationFilter.USE_ALL).migrate(flyway);
        }
    }
}
