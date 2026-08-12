package com.ticket.core.config.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DeprecatedHoldEndpointMetricsFilterTest {

    @Test
    void records_fixed_status_family_without_performance_identifier_tag() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DeprecatedHoldEndpointMetricsFilter filter = filter(meterRegistry);
        MockHttpServletRequest request = request("POST", "/api/v1/performances/987654/holds");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(409));

        assertThat(meterRegistry.get("booking.api.deprecated.hold.request")
                .tag("status", "4xx")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .noneMatch(tag -> tag.getValue().equals("987654")));
    }

    @Test
    void ignores_successor_order_endpoint_and_non_post_requests() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DeprecatedHoldEndpointMetricsFilter filter = filter(meterRegistry);

        filter.doFilter(
                request("POST", "/api/v1/orders"),
                new MockHttpServletResponse(),
                (request, response) -> { }
        );
        filter.doFilter(
                request("GET", "/api/v1/performances/10/holds"),
                new MockHttpServletResponse(),
                (request, response) -> { }
        );

        assertThat(meterRegistry.getMeters())
                .allSatisfy(meter -> assertThat(meter.measure())
                        .allSatisfy(measurement -> assertThat(measurement.getValue()).isZero()));
    }

    @Test
    void supports_application_context_path() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DeprecatedHoldEndpointMetricsFilter filter = filter(meterRegistry);
        MockHttpServletRequest request = request("POST", "/ticket/api/v1/performances/10/holds");
        request.setContextPath("/ticket");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> { });

        assertThat(meterRegistry.get("booking.api.deprecated.hold.request")
                .tag("status", "2xx")
                .counter()
                .count()).isEqualTo(1.0);
    }

    private MockHttpServletRequest request(final String method, final String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setRequestURI(requestUri);
        return request;
    }

    private DeprecatedHoldEndpointMetricsFilter filter(final SimpleMeterRegistry meterRegistry) {
        return new DeprecatedHoldEndpointMetricsFilter(Optional.of(meterRegistry));
    }
}
