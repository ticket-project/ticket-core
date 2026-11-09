package com.ticket.booking.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsSci;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.config.annotation.WebMvcStompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import com.ticket.shared.config.CorsProperties;

/**
 * 실제 Tomcat에 {@link WebSocketConfig}가 등록한 {@code /ws} SockJS handler를 올려, 클라이언트가 permessage-deflate를 제안해도 handshake 응답이
 * 고르지 않는지 본다. Tomcat은 Spring이 고른 확장 목록이 비어 있으면 자기 기본 확장(permessage-deflate)으로 협상하므로, 단위 검사로는 이 동작을 확인할 수 없다.
 */
@SuppressWarnings("NonAsciiCharacters")
class WebSocketHandshakeCompressionTest {
    private static final String ORIGIN = "http://localhost:3000";

    private Tomcat tomcat;
    private ThreadPoolTaskScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        final CorsProperties corsProperties = mock(CorsProperties.class);
        when(corsProperties.allowedOrigins()).thenReturn(List.of(ORIGIN));
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        final WebMvcStompEndpointRegistry registry = new WebMvcStompEndpointRegistry(
                new SubProtocolWebSocketHandler(mock(MessageChannel.class), mock(SubscribableChannel.class)),
                new WebSocketTransportRegistration(),
                scheduler);
        new WebSocketConfig(mock(WebSocketAuthInterceptor.class), corsProperties).registerStompEndpoints(registry);
        final HttpRequestHandler sockJs = (HttpRequestHandler) ((SimpleUrlHandlerMapping) registry.getHandlerMapping())
                .getUrlMap()
                .get("/ws/**");

        tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("ws-handshake").toString());
        tomcat.setPort(0);
        final Context context = tomcat.addContext("", null);
        context.addServletContainerInitializer(new WsSci(), null);
        Tomcat.addServlet(context, "sockjs", new HttpServlet() {
            @Override
            protected void service(final HttpServletRequest request, final HttpServletResponse response)
                    throws IOException {
                // DispatcherServlet이 넣어 주는 "handler mapping 안 경로"를 대신 넣는다.
                request.setAttribute(
                        HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                        request.getRequestURI().substring("/ws/".length()));
                try {
                    sockJs.handleRequest(request, response);
                } catch (final jakarta.servlet.ServletException e) {
                    throw new IOException(e);
                }
            }
        });
        context.addServletMappingDecoded("/ws/*", "sockjs");
        tomcat.getConnector();
        tomcat.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        tomcat.stop();
        tomcat.destroy();
        scheduler.shutdown();
    }

    @Test
    void raw_websocket_handshake는_permessage_deflate를_고르지_않는다() throws IOException {
        final String response = handshake("/ws/websocket");

        assertThat(response).startsWith("HTTP/1.1 101");
        assertThat(response.toLowerCase()).doesNotContain("permessage-deflate");
    }

    @Test
    void sockjs_websocket_transport_handshake도_permessage_deflate를_고르지_않는다() throws IOException {
        final String response = handshake("/ws/000/session1/websocket");

        assertThat(response).startsWith("HTTP/1.1 101");
        assertThat(response.toLowerCase()).doesNotContain("permessage-deflate");
    }

    /** 브라우저처럼 permessage-deflate를 제안하는 upgrade 요청을 보내고 응답 헤더를 돌려준다. */
    private String handshake(final String path) throws IOException {
        try (Socket socket = new Socket("localhost", tomcat.getConnector().getLocalPort())) {
            socket.setSoTimeout(5_000);
            final OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\n"
                            + "Host: localhost\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Connection: Upgrade\r\n"
                            + "Origin: " + ORIGIN + "\r\n"
                            + "Sec-WebSocket-Version: 13\r\n"
                            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                            + "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits\r\n"
                            + "\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            final InputStream in = socket.getInputStream();
            final ByteArrayOutputStream headers = new ByteArrayOutputStream();
            while (!headers.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
                final int b = in.read();
                if (b < 0) {
                    break;
                }
                headers.write(b);
            }
            return headers.toString(StandardCharsets.US_ASCII);
        }
    }
}
