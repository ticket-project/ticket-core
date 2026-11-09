package com.ticket.bootstrap.scaleout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.GenericContainer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticket.TicketApplication;
import com.ticket.booking.order.usecase.ExpirePendingOrdersUseCase;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.api.SocialProvider;
import com.ticket.security.jwt.JwtAuthTokenIssuer;
import com.ticket.testsupport.PostgreSqlTestDatabase;
import com.ticket.testsupport.TestContainerImages;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Core 두 대(A, B)가 같은 PostgreSQL·Redis를 쓸 때 요청이 어느 서버로 가도 같은 결과가 나오는지 확인한다(스케일 아웃 결정 D3-3 전 검증).
 *
 * <p>한 JVM에 애플리케이션 컨텍스트 두 개를 띄운다. 운영의 ALB처럼 sticky 없이 요청마다 서버를 바꿔 보낸다. 다른 테스트와 데이터가 섞이지 않게 자기 schema와 자기 Redis를 쓰고, 카카오
 * 로그인은 이 테스트가 띄운 가짜 제공자(token·user-info)로 끝까지 관통한다.
 */
@SuppressWarnings({"NonAsciiCharacters", "resource"})
class TwoInstanceE2ETest {
    private static final int REDIS_PORT = 6379;
    private static final long PERFORMANCE_ID = 920000001L;
    private static final List<Long> SEAT_IDS = List.of(920000001L, 920000002L, 920000003L, 920000004L);
    private static final String FRONTEND_ORIGIN = "http://localhost:3000";
    private static final String ORDER_TERMINATED = "com.ticket.booking.OrderTerminated";
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);
    /** 요청을 만들 때는 서버를 정하지 않는다. {@link #send}가 보낼 서버 주소로 바꾼다. */
    private static final String PLACEHOLDER = "http://server";

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final AtomicLong KAKAO_USER_SEQUENCE = new AtomicLong(1000);

    private static HttpServer kakao;
    private static ConfigurableApplicationContext a;
    private static ConfigurableApplicationContext b;

    @BeforeAll
    static void startInstances() throws IOException {
        REDIS.start();
        kakao = startFakeKakao();
        final String dbUrl = PostgreSqlTestDatabase.newSchemaUrl("two_instance");
        // 차례로 띄운다. A가 migration을 적용하고 B는 이미 적용된 schema를 검증만 한다(롤링 배포의 두 번째 서버와 같다).
        a = start(dbUrl);
        b = start(dbUrl);
    }

    @AfterAll
    static void stopInstances() {
        if (b != null) {
            b.close();
        }
        if (a != null) {
            a.close();
        }
        if (kakao != null) {
            kakao.stop(0);
        }
        REDIS.stop();
    }

    @BeforeEach
    void resetData() {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("fixture/booking-e2e-reset.sql"),
                new ClassPathResource("fixture/booking-e2e-fixture.sql"));
        populator.execute(a.getBean(DataSource.class));
        try (RedisConnection connection =
                a.getBean(RedisConnectionFactory.class).getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    // 로그인 ------------------------------------------------------------------

    /** 로그인 시작(A) → 제공자 콜백(B) → 일회용 코드 교환(A) → 재발급(B) → 로그아웃(A) → 재발급(B) 거절. */
    @Test
    void 로그인_단계마다_다른_서버로_가도_로그인과_로그아웃이_된다() throws Exception {
        final HttpResponse<String> authorize = send(a, get("/api/v1/auth/oauth2/authorize/kakao"));
        assertThat(authorize.statusCode()).isEqualTo(302);
        final String state = UriComponentsBuilder.fromUriString(location(authorize))
                .build()
                .getQueryParams()
                .getFirst("state");
        assertThat(state).isNotBlank();
        final String requestCookie = cookie(authorize, "__Host-oauth2_req_");

        final HttpResponse<String> callback = send(
                b,
                get("/api/v1/auth/oauth2/callback/kakao?code=provider-code&state=" + state)
                        .header("Cookie", requestCookie));
        assertThat(callback.statusCode()).isEqualTo(302);
        final String frontendRedirect = location(callback);
        assertThat(frontendRedirect)
                .as("B가 A에서 시작한 로그인을 이어받지 못했다")
                .startsWith(FRONTEND_ORIGIN + "/auth/callback?code=")
                .doesNotContain("error");
        final String authCode = UriComponentsBuilder.fromUriString(frontendRedirect)
                .build()
                .getQueryParams()
                .getFirst("code");

        final HttpResponse<String> exchange =
                send(a, postJson("/api/v1/auth/oauth2/token", "{\"code\":\"" + authCode + "\"}"));
        assertThat(exchange.statusCode()).as("코드 교환: %s", exchange.body()).isEqualTo(200);
        final String refreshCookie = cookie(exchange, "refresh_token=");

        final HttpResponse<String> refresh =
                send(b, postJson("/api/v1/auth/refresh", "").header("Cookie", refreshCookie));
        assertThat(refresh.statusCode()).as("재발급: %s", refresh.body()).isEqualTo(200);
        final String accessToken = data(refresh).get("accessToken").asString();
        final String rotatedRefreshCookie = cookie(refresh, "refresh_token=");

        final HttpResponse<String> logout = send(
                a,
                postJson("/api/v1/auth/logout", "")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Cookie", rotatedRefreshCookie));
        assertThat(logout.statusCode()).as("로그아웃: %s", logout.body()).isEqualTo(200);

        final HttpResponse<String> refreshAfterLogout =
                send(b, postJson("/api/v1/auth/refresh", "").header("Cookie", rotatedRefreshCookie));
        assertThat(refreshAfterLogout.statusCode())
                .as("A에서 로그아웃한 재발급 토큰을 B가 받아 줬다")
                .isEqualTo(401);
    }

    // 좌석 알림 -----------------------------------------------------------------

    @Test
    void 한_서버에서_좌석을_고르면_다른_서버에_연결한_사용자도_알림을_받는다() throws Exception {
        final String token = loginAsNewMember(a, "notify@two-instance.test");
        final String topic = "/topic/performance/" + PERFORMANCE_ID + "/seats";
        final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();

        final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        final WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin(FRONTEND_ORIGIN);
        final StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        final StompSession session = stompClient
                .connectAsync(
                        "ws://localhost:" + port(b) + "/ws/websocket",
                        handshakeHeaders,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
        try {
            session.subscribe(topic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(final StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(final StompHeaders headers, final Object payload) {
                    received.add((JsonNode) payload);
                }
            });
            // 구독은 B가 비동기로 등록한다. 등록되기 전에 고르면 알림을 놓친다.
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> !b.getBean(SimpUserRegistry.class)
                            .findSubscriptions(s -> topic.equals(s.getDestination()))
                            .isEmpty());

            final long seatId = SEAT_IDS.get(0);
            final HttpResponse<String> select = send(a, authed(selectPath(seatId), token, "POST"));
            assertThat(select.statusCode()).as("좌석 선택: %s", select.body()).isEqualTo(200);

            final JsonNode message = received.poll(ASYNC_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(message).as("A에서 고른 좌석 알림이 B의 구독자에게 오지 않았다").isNotNull();
            assertThat(message.toString()).contains(String.valueOf(seatId));
        } finally {
            session.disconnect();
            stompClient.stop();
        }
    }

    // 예매 ------------------------------------------------------------------

    @Test
    void 예매_요청을_두_서버에_번갈아_보내도_같은_좌석_상태를_본다() throws Exception {
        final String owner = loginAsNewMember(a, "owner@two-instance.test");
        final String rival = loginAsNewMember(a, "rival@two-instance.test");
        final long seatId = SEAT_IDS.get(1);

        assertThat(send(a, authed(selectPath(seatId), owner, "POST")).statusCode())
                .isEqualTo(200);
        final HttpResponse<String> rivalSelect = send(b, authed(selectPath(seatId), rival, "POST"));
        assertThat(rivalSelect.statusCode())
                .as("A에서 잡힌 좌석을 B가 다른 사람에게 줬다: %s", rivalSelect.body())
                .isEqualTo(409);

        final HttpResponse<String> create = send(b, createOrder(owner, seatId));
        assertThat(create.statusCode()).as("주문 생성: %s", create.body()).isEqualTo(201);
        final String orderKey = data(create).get("orderKey").asString();
        await().atMost(ASYNC_TIMEOUT).until(() -> "OCCUPIED".equals(seatStatus(a, owner, seatId)));

        final HttpResponse<String> cancel = send(a, authed("/api/v1/orders/" + orderKey, owner, "DELETE"));
        assertThat(cancel.statusCode()).as("주문 취소: %s", cancel.body()).isEqualTo(200);
        await().atMost(ASYNC_TIMEOUT).until(() -> "AVAILABLE".equals(seatStatus(b, owner, seatId)));
    }

    // 정기 작업 ---------------------------------------------------------------

    /** 두 서버의 주문 만료 청소가 동시에 돌아도 주문마다 한 번만 만료하고 종료 이벤트도 한 번만 낸다(D3-1). */
    @Test
    void 두_서버가_동시에_만료_청소를_돌려도_주문마다_한_번만_만료한다() throws Exception {
        final List<String> orderKeys = new ArrayList<>();
        for (int i = 0; i < SEAT_IDS.size(); i++) {
            final ConfigurableApplicationContext server = i % 2 == 0 ? a : b;
            final String token = loginAsNewMember(a, "expire-" + i + "@two-instance.test");
            final long seatId = SEAT_IDS.get(i);
            assertThat(send(server, authed(selectPath(seatId), token, "POST")).statusCode())
                    .isEqualTo(200);
            final HttpResponse<String> create = send(server, createOrder(token, seatId));
            assertThat(create.statusCode()).as("주문 생성: %s", create.body()).isEqualTo(201);
            orderKeys.add(data(create).get("orderKey").asString());
        }
        final JdbcTemplate jdbc = new JdbcTemplate(a.getBean(DataSource.class));
        // 앱 시계와 DB 시간대가 달라도 확실히 지난 시각이 되게 하루를 뺀다.
        jdbc.update("UPDATE orders SET expires_at = expires_at - INTERVAL '1 day' WHERE status = 'PENDING'");

        final CountDownLatch start = new CountDownLatch(1);
        final List<CompletableFuture<ExpirePendingOrdersUseCase.Output>> runs = new ArrayList<>();
        for (final ConfigurableApplicationContext server : List.of(a, b, a, b)) {
            runs.add(CompletableFuture.supplyAsync(() -> {
                awaitLatch(start);
                return server.getBean(ExpirePendingOrdersUseCase.class).execute();
            }));
        }
        start.countDown();
        for (final CompletableFuture<ExpirePendingOrdersUseCase.Output> run : runs) {
            assertThat(run.get(30, TimeUnit.SECONDS).failedCount()).isZero();
        }

        // 각 서버의 5초 주기 청소도 함께 돌고 있다. 직접 부른 청소가 다 끝난 뒤라 남은 주문은 없어야 한다.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE status = 'PENDING'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE status = 'EXPIRED' AND order_key IN (?, ?, ?, ?)",
                        Integer.class,
                        orderKeys.toArray()))
                .isEqualTo(orderKeys.size());
        assertThat(jdbc.queryForObject(
                        "SELECT (SELECT COUNT(*) FROM event_publication WHERE event_type = ?)"
                                + " + (SELECT COUNT(*) FROM event_publication_archive WHERE event_type = ?)",
                        Integer.class,
                        ORDER_TERMINATED,
                        ORDER_TERMINATED))
                .as("같은 주문의 종료 이벤트가 두 번 나갔다")
                .isEqualTo(orderKeys.size());
    }

    // 기동 ------------------------------------------------------------------

    private static ConfigurableApplicationContext start(final String dbUrl) {
        final String kakaoBase = "http://127.0.0.1:" + kakao.getAddress().getPort();
        final Map<String, Object> properties = new HashMap<>();
        properties.put("server.port", 0);
        properties.put("spring.datasource.url", dbUrl);
        properties.put("spring.datasource.username", PostgreSqlTestDatabase.username());
        properties.put("spring.datasource.password", PostgreSqlTestDatabase.password());
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("spring.flyway.enabled", true);
        properties.put("spring.flyway.locations", "classpath:db/migration-vendor/postgresql");
        properties.put("spring.data.redis.host", REDIS.getHost());
        properties.put("spring.data.redis.port", REDIS.getMappedPort(REDIS_PORT));
        properties.put("spring.security.oauth2.client.provider.kakao.token-uri", kakaoBase + "/token");
        properties.put("spring.security.oauth2.client.provider.kakao.user-info-uri", kakaoBase + "/userinfo");
        // properties()는 가장 낮은 우선순위의 기본값이라 application.yml(spring.flyway.enabled: false 등)에 가려진다. 명령행 인수로 넘긴다.
        return new SpringApplicationBuilder(TicketApplication.class)
                .run(properties.entrySet().stream()
                        .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new));
    }

    /** 카카오 token·user-info 응답만 흉내 낸다. 로그인마다 새 카카오 회원 id를 준다. */
    private static HttpServer startFakeKakao() throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/token",
                exchange -> respondJson(
                        exchange,
                        "{\"access_token\":\"fake-kakao-token\",\"token_type\":\"bearer\",\"expires_in\":3600}"));
        server.createContext("/userinfo", exchange -> {
            final long id = KAKAO_USER_SEQUENCE.incrementAndGet();
            respondJson(
                    exchange,
                    "{\"id\":" + id + ",\"kakao_account\":{\"email\":\"kakao-" + id + "@two-instance.test\","
                            + "\"is_email_valid\":true,\"is_email_verified\":true,"
                            + "\"profile\":{\"nickname\":\"two-instance\"}}}");
        });
        server.start();
        return server;
    }

    private static void respondJson(final HttpExchange exchange, final String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // 요청 ------------------------------------------------------------------

    private static int port(final ConfigurableApplicationContext server) {
        return Integer.parseInt(server.getEnvironment().getRequiredProperty("local.server.port"));
    }

    /** ALB 뒤처럼 모든 서버가 같은 외부 주소를 보게 한다. 소셜 로그인의 redirect_uri가 서버마다 같아야 다른 서버가 콜백을 받는다. */
    private static HttpResponse<String> send(
            final ConfigurableApplicationContext server, final HttpRequest.Builder request)
            throws IOException, InterruptedException {
        final URI path = request.copy().build().uri();
        final HttpRequest built = request.copy()
                .uri(URI.create("http://localhost:" + port(server) + path.getRawPath()
                        + (path.getRawQuery() == null ? "" : "?" + path.getRawQuery())))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "ticket.test")
                .header("X-Forwarded-Port", "443")
                .build();
        return HTTP.send(built, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.Builder get(final String path) {
        return HttpRequest.newBuilder(URI.create(PLACEHOLDER + path)).GET();
    }

    private static HttpRequest.Builder postJson(final String path, final String body) {
        return HttpRequest.newBuilder(URI.create(PLACEHOLDER + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private static HttpRequest.Builder authed(final String path, final String token, final String method) {
        return HttpRequest.newBuilder(URI.create(PLACEHOLDER + path))
                .header("Authorization", "Bearer " + token)
                .method(method, HttpRequest.BodyPublishers.noBody());
    }

    private static HttpRequest.Builder createOrder(final String token, final long seatId) {
        return postJson("/api/v1/orders", "{\"performanceId\":" + PERFORMANCE_ID + ",\"seatIds\":[" + seatId + "]}")
                .header("Authorization", "Bearer " + token);
    }

    private static String selectPath(final long seatId) {
        return "/api/v1/performances/" + PERFORMANCE_ID + "/seats/" + seatId + "/select";
    }

    private static String seatStatus(final ConfigurableApplicationContext server, final String token, final long seatId)
            throws Exception {
        final HttpResponse<String> response =
                send(server, authed("/api/v1/performances/" + PERFORMANCE_ID + "/seats/status", token, "GET"));
        for (final JsonNode seat : data(response).get("seats")) {
            if (seat.get("seatId").asLong() == seatId) {
                return seat.get("status").asString();
            }
        }
        throw new AssertionError("좌석 " + seatId + "가 응답에 없다: " + response.body());
    }

    private static String location(final HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow(() -> new AssertionError("Location이 없다"));
    }

    /** 이름이 {@code prefix}로 시작하는 Set-Cookie에서 요청에 다시 실을 {@code 이름=값}만 꺼낸다. */
    private static String cookie(final HttpResponse<String> response, final String prefix) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(0, value.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError(prefix + " 쿠키가 없다: " + response.headers()));
    }

    private static JsonNode data(final HttpResponse<String> response) {
        final JsonNode data = JSON.readTree(response.body()).get("data");
        assertThat(data).as("응답에 data가 없다: %s", response.body()).isNotNull();
        return data;
    }

    /** 가입·로그인은 위 로그인 테스트가 관통한다. 나머지 테스트는 그 끝에서 부르는 회원 계약과 토큰 발급기를 쓴다. */
    private static String loginAsNewMember(final ConfigurableApplicationContext server, final String email) {
        final AuthenticatedMember member = server.getBean(MemberAccountApi.class)
                .resolveSocialAccount(new SocialIdentity(
                        SocialProvider.GOOGLE, "two-instance-" + email, email, true, email.substring(0, 5)));
        return server.getBean(JwtAuthTokenIssuer.class)
                .issueTokens(member.memberId(), member.role())
                .accessToken();
    }

    private static void awaitLatch(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
