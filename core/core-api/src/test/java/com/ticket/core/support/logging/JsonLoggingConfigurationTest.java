package com.ticket.core.support.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JsonLoggingConfigurationTest {

    private static final String ANSI_ESCAPE = String.valueOf((char) 0x1B);

    private final LoggerContext loggerContext = new LoggerContext();
    private final StructuredLogEncoder encoder = new StructuredLogEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        encoder.stop();
        loggerContext.stop();
    }

    @Test
    void logging설정은_공통_Logstash_JSON_설정을_사용한다() throws Exception {
        var loggingProperties = new YamlPropertySourceLoader()
                .load("logging", new ClassPathResource("logging.yml"));

        assertThat(loggingProperties).hasSize(1);
        assertThat(loggingProperties.getFirst().getProperty("logging.structured.format.console"))
                .isEqualTo("logstash");
        assertThat(loggingProperties.getFirst().getProperty("logging.level.com.ticket"))
                .isEqualTo("INFO");

        var applicationProperties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        assertThat(applicationProperties.getFirst().getProperty("spring.main.banner-mode"))
                .isEqualTo("off");
    }

    @Test
    void 로그는_MDC와_key_value와_예외를_한줄_JSON으로_출력한다() throws Exception {
        loggerContext.putObject(Environment.class.getName(), new MockEnvironment());
        encoder.setContext(loggerContext);
        encoder.setFormat("logstash");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        var event = new LoggingEvent(
                getClass().getName(),
                loggerContext.getLogger(getClass()),
                Level.ERROR,
                "결제 처리 실패",
                new IllegalStateException("boom"),
                null
        );
        event.setMDCPropertyMap(Map.of(
                "traceId", "trace-123",
                "spanId", "span-456",
                "dd.trace_id", "123456789",
                "dd.span_id", "987654321"
        ));
        event.addKeyValuePair(new KeyValuePair("event", "payment.failed"));
        event.prepareForDeferredProcessing();

        var output = new String(encoder.encode(event), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(output);

        assertThat(output.lines()).hasSize(1);
        assertThat(output).doesNotContain(ANSI_ESCAPE);
        assertThat(json.path("@timestamp").asText()).isNotBlank();
        assertThat(json.path("@version").asText()).isEqualTo("1");
        assertThat(json.path("message").asText()).isEqualTo("결제 처리 실패");
        assertThat(json.path("logger_name").asText()).isNotBlank();
        assertThat(json.path("thread_name").asText()).isNotBlank();
        assertThat(json.path("level").asText()).isEqualTo("ERROR");
        assertThat(json.path("level_value").asInt()).isEqualTo(40_000);
        assertThat(json.path("traceId").asText()).isEqualTo("trace-123");
        assertThat(json.path("spanId").asText()).isEqualTo("span-456");
        assertThat(json.path("dd.trace_id").asText()).isEqualTo("123456789");
        assertThat(json.path("dd.span_id").asText()).isEqualTo("987654321");
        assertThat(json.path("event").asText()).isEqualTo("payment.failed");
        assertThat(json.path("stack_trace").asText())
                .contains("IllegalStateException", "boom");
    }
}
