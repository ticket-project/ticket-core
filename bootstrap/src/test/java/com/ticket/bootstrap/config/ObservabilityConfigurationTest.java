package com.ticket.bootstrap.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTest {

    @Test
    void exposesCapacityMetricsWithStableTagsAndHistograms() {
        final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        final Properties properties = Objects.requireNonNull(yaml.getObject());

        assertThat(properties)
                .containsEntry("spring.application.name", "ticket-core")
                .containsEntry("spring.datasource.hikari.pool-name", "ticket-core")
                .containsEntry("server.tomcat.mbeanregistry.enabled", true)
                .containsEntry("management.endpoints.web.exposure.include", "health,info,prometheus")
                .containsEntry("management.metrics.tags.service", "${DD_SERVICE:ticket-core}")
                .containsEntry("management.metrics.tags.environment", "${DD_ENV:local}")
                .containsEntry("management.metrics.tags.version", "${DD_VERSION:unknown}")
                .containsEntry(
                        "management.metrics.distribution.percentiles-histogram.http.server.requests",
                        true
                )
                .containsEntry(
                        "management.metrics.distribution.slo.http.server.requests",
                        "100ms,300ms,500ms,1s,2s,3s,5s"
                );
    }
}
