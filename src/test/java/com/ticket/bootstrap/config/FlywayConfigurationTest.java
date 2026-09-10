package com.ticket.bootstrap.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayConfigurationTest {

    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    @Test
    void flyway_core_oracle_database_module_and_boot_auto_configuration_are_on_test_runtime_classpath() {
        final ClassLoader classLoader = getClass().getClassLoader();

        assertThat(ClassUtils.isPresent("org.flywaydb.core.Flyway", classLoader)).isTrue();
        assertThat(ClassUtils.isPresent("org.flywaydb.database.oracle.OracleDatabaseType", classLoader)).isTrue();
        assertThat(ClassUtils.isPresent("org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration", classLoader)).isTrue();
    }

    @Test
    void common_flyway_settings_disable_accidental_migration_by_default() throws Exception {
        final PropertySource<?> application = loadYaml("application.yml");

        assertThat(application.getProperty("spring.flyway.enabled")).isEqualTo(false);
        assertThat(application.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(application.getProperty("spring.flyway.encoding")).isEqualTo("UTF-8");
        assertThat(application.getProperty("spring.flyway.clean-disabled")).isEqualTo(true);
        assertThat(application.getProperty("spring.flyway.validate-on-migrate")).isEqualTo(true);
    }

    @Test
    void modulith_runtime_flyway_is_enabled_to_split_migrations_per_module() throws Exception {
        final PropertySource<?> application = loadYaml("application.yml");

        assertThat(application.getProperty("spring.modulith.runtime.flyway-enabled")).isEqualTo(true);
    }

    @Test
    void local_profile_bootstraps_h2_database_with_hibernate_ddl() throws Exception {
        final PropertySource<?> local = loadYaml("application-local.yml");

        assertThat(local.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:file:~/ticket-local;MODE=Oracle;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1");
        assertThat(local.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create");
        assertThat(local.getProperty("spring.jpa.defer-datasource-initialization")).isEqualTo(true);
        assertThat(local.getProperty("spring.flyway.enabled")).isEqualTo(false);
    }

    @Test
    void dev_profile_reuses_local_h2_schema_with_flyway() throws Exception {
        final PropertySource<?> dev = loadYaml("application-dev.yml");

        assertThat(dev.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:file:~/ticket-local;MODE=Oracle;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1");
        assertThat(dev.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(dev.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(dev.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("${SPRING_FLYWAY_BASELINE_ON_MIGRATE:false}");
        assertThat(dev.getProperty("spring.flyway.baseline-version")).isEqualTo("1");
        assertThat(dev.getProperty("spring.flyway.baseline-description")).isEqualTo("existing local schema before Flyway");
    }

    @Test
    void prod_profile_enables_flyway_with_existing_schema_baseline() throws Exception {
        final PropertySource<?> prod = loadYaml("application-prod.yml");

        assertThat(prod.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(prod.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("${SPRING_FLYWAY_BASELINE_ON_MIGRATE:false}");
        assertThat(prod.getProperty("spring.flyway.baseline-version")).isEqualTo("1");
        assertThat(prod.getProperty("spring.flyway.baseline-description")).isEqualTo("existing schema before Flyway");
        assertThat(prod.getProperty("spring.flyway.clean-disabled")).isEqualTo(true);
    }

    /**
     * 초기 데이터 적재는 애플리케이션 기동에서 빠졌다(별도 {@code seedLocal} 명령이 맡는다).
     * {@code app.seed.*} 설정이 조용히 다시 들어오는 것을 막는다 — 남아 있으면 "기동하면
     * 데이터가 들어간다"는 잘못된 기대가 되살아난다.
     */
    @Test
    void no_profile_declares_startup_seed_properties() throws Exception {
        for (final String resourceName : List.of("application.yml", "application-local.yml",
                "application-dev.yml", "application-prod.yml")) {
            final PropertySource<?> propertySource = loadYaml(resourceName);

            assertThat(propertySource.getProperty("app.seed.enabled"))
                    .as("%s에 app.seed.enabled가 남아 있다", resourceName)
                    .isNull();
            assertThat(propertySource.getProperty("app.seed.load-test-fixture.enabled"))
                    .as("%s에 app.seed.load-test-fixture.enabled가 남아 있다", resourceName)
                    .isNull();
            assertThat(propertySource.getProperty("app.seed.load-test-members.count"))
                    .as("%s에 app.seed.load-test-members.count가 남아 있다", resourceName)
                    .isNull();
        }
    }

    private PropertySource<?> loadYaml(final String resourceName) throws IOException {
        final List<PropertySource<?>> propertySources = yamlLoader.load(resourceName, new ClassPathResource(resourceName));
        assertThat(propertySources).hasSize(1);
        return propertySources.getFirst();
    }
}
