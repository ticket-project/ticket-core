package com.ticket.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

class FlywayConfigurationTest {
    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    @Test
    void flyway_postgresql_and_boot_auto_configuration_are_on_test_runtime_classpath() {
        final ClassLoader classLoader = getClass().getClassLoader();

        assertThat(ClassUtils.isPresent("org.flywaydb.core.Flyway", classLoader))
                .isTrue();
        assertThat(ClassUtils.isPresent("org.flywaydb.database.postgresql.PostgreSQLDatabaseType", classLoader))
                .isTrue();
        assertThat(ClassUtils.isPresent(
                        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration", classLoader))
                .isTrue();
    }

    /** 공통 설정에서 migration은 꺼져 있고, 켜는 profile에서도 clean은 막히고 migrate 전 검증은 켜져 있어야 한다. */
    @Test
    void common_flyway_settings_disable_accidental_migration_by_default() throws Exception {
        final PropertySource<?> application = loadYaml("application.yml");

        assertThat(application.getProperty("spring.flyway.enabled")).isEqualTo(false);
        assertThat(application.getProperty("spring.flyway.clean-disabled")).isEqualTo(true);
        assertThat(application.getProperty("spring.flyway.validate-on-migrate")).isEqualTo(true);
    }

    @Test
    void modulith_runtime_flyway_is_enabled_to_split_migrations_per_module() throws Exception {
        final PropertySource<?> application = loadYaml("application.yml");

        assertThat(application.getProperty("spring.modulith.runtime.flyway-enabled"))
                .isEqualTo(true);
    }

    /** 스키마 원본은 migration이다(ADR 0020). DB를 쓰는 profile은 모두 Flyway로 스키마를 만들고 Hibernate는 검증만 한다. */
    @ParameterizedTest
    @ValueSource(strings = {"application-local.yml", "application-prod.yml"})
    void db_profiles_build_schema_with_flyway_and_hibernate_only_validates(final String resourceName) throws Exception {
        final PropertySource<?> profile = loadYaml(resourceName);

        assertThat(profile.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(profile.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(String.valueOf(profile.getProperty("spring.flyway.locations"))
                        .split(","))
                .containsExactly("classpath:db/migration-vendor/postgresql");
        assertThat(profile.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.postgresql.Driver");
        assertThat(profile.getProperty("spring.jpa.database-platform")).isNull();
        assertThat(profile.getProperty("spring.flyway.clean-disabled")).isNotEqualTo(false);
        assertNoIgnoredBaselineSettings(profile);
    }

    /**
     * {@code SpringModulithFlywayMigrationStrategy}는 {@code __root}와 module마다 Flyway를 새로 만들면서
     * {@code baselineOnMigrate=true}, {@code baselineVersion=0}을 강제한다. 이 두 값을 yml에 적어도 적용되지 않으므로, 적용되는 것처럼 보이는 설정이 다시
     * 들어오지 않게 막는다.
     */
    private void assertNoIgnoredBaselineSettings(final PropertySource<?> profile) {
        assertThat(profile.getProperty("spring.flyway.baseline-on-migrate")).isNull();
        assertThat(profile.getProperty("spring.flyway.baseline-version")).isNull();
    }

    private PropertySource<?> loadYaml(final String resourceName) throws IOException {
        final List<PropertySource<?>> propertySources =
                yamlLoader.load(resourceName, new ClassPathResource(resourceName));
        assertThat(propertySources).hasSize(1);
        return propertySources.getFirst();
    }
}
