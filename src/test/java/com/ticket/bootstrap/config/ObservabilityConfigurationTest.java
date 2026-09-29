package com.ticket.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * 용량 판정이 기대는 관측 전제만 고정한다 — prometheus 노출, 서비스 구분 tag, 요청 지연 histogram, Tomcat·Hikari 지표. 기본값 문자열이나 SLO 구간 같은 조정 값은 yml이
 * 원본이라 여기서 다시 적지 않는다.
 */
class ObservabilityConfigurationTest {
    @Test
    void exposesCapacityMetricsWithStableTagsAndHistograms() {
        final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        final Properties properties = Objects.requireNonNull(yaml.getObject());

        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .contains("prometheus");
        assertThat(properties)
                .containsKeys(
                        "spring.datasource.hikari.pool-name",
                        "management.metrics.tags.service",
                        "management.metrics.tags.environment",
                        "management.metrics.tags.version",
                        "management.metrics.distribution.slo.http.server.requests")
                .containsEntry("server.tomcat.mbeanregistry.enabled", true)
                .containsEntry("management.metrics.distribution.percentiles-histogram.http.server.requests", true);
    }
}
