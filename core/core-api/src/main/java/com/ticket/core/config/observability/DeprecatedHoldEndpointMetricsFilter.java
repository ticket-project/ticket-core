package com.ticket.core.config.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DeprecatedHoldEndpointMetricsFilter extends OncePerRequestFilter {

    private static final String METRIC_NAME = "booking.api.deprecated.hold.request";
    private static final Pattern HOLD_PATH = Pattern.compile(
            "^/api/v1/performances/[^/]+/holds/?$"
    );

    private final MeterRegistry meterRegistry;

    public DeprecatedHoldEndpointMetricsFilter(final Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.orElse(null);
        if (this.meterRegistry != null) {
            List.of("2xx", "4xx", "5xx", "other")
                    .forEach(status -> counter(status));
        }
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return meterRegistry == null
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !HOLD_PATH.matcher(applicationPath(request)).matches();
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            counter(statusFamily(response.getStatus())).increment();
        }
    }

    private String applicationPath(final HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }

    private String statusFamily(final int status) {
        return switch (status / 100) {
            case 2 -> "2xx";
            case 4 -> "4xx";
            case 5 -> "5xx";
            default -> "other";
        };
    }

    private Counter counter(final String status) {
        return meterRegistry.counter(METRIC_NAME, "status", status);
    }
}
