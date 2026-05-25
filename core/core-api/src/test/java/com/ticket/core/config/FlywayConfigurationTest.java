package com.ticket.core.config;

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
    void local_profile_keeps_hibernate_create_flow_and_disables_flyway() throws Exception {
        final PropertySource<?> local = loadYaml("application-local.yml");

        assertThat(local.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create");
        assertThat(local.getProperty("spring.flyway.enabled")).isEqualTo(false);
    }

    @Test
    void prod_profile_enables_flyway_with_existing_schema_baseline() throws Exception {
        final PropertySource<?> prod = loadYaml("application-prod.yml");

        assertThat(prod.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(prod.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("${SPRING_FLYWAY_BASELINE_ON_MIGRATE:false}");
        assertThat(prod.getProperty("spring.flyway.baseline-version")).isEqualTo("1");
        assertThat(prod.getProperty("spring.flyway.baseline-description")).isEqualTo("existing schema before Flyway");
        assertThat(prod.getProperty("spring.flyway.clean-disabled")).isEqualTo(true);
    }

    private PropertySource<?> loadYaml(final String resourceName) throws IOException {
        final List<PropertySource<?>> propertySources = yamlLoader.load(resourceName, new ClassPathResource(resourceName));
        assertThat(propertySources).hasSize(1);
        return propertySources.getFirst();
    }
}
