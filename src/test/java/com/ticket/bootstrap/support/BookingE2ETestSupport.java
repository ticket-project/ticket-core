package com.ticket.bootstrap.support;

import tools.jackson.databind.JsonNode;
import com.ticket.TicketApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.fail;

/**
 * 실제 스택을 관통하는 통합 테스트의 베이스다.
 *
 * <p>단위 테스트는 계층마다 mock을 끼우므로 각 층이 자기 mock에 대해 맞으면 통과한다. 층 사이를
 * 이어 붙였을 때 어긋나는 것(Redis key 불일치, 커밋과 커밋 후 처리의 순서, 트랜잭션 경계)은
 * 진짜 HTTP로 진짜 스택을 두드려야 드러난다.
 *
 * <p>worker.enabled는 기본값(true)을 그대로 둔다. 끄면 하위 클래스마다 프로퍼티를 재정의해야 해서
 * Spring 컨텍스트가 갈라지고, 스케줄러 주기가 5분과 2분이라 초 단위로 끝나는 테스트를 방해하지
 * 않는다. 대신 fixture의 hold_time을 넉넉히 두어 만료가 끼어들지 않게 한다.
 */
@SpringBootTest(
        classes = TicketApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:booking-e2e;MODE=Oracle;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "app.seed.enabled=false",
                "app.seed.load-test-fixture.enabled=false",
                "JWT_SECRET=0123456789abcdef0123456789abcdef",
                "JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=1800",
                "JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=1209600",
                "GOOGLE_CLIENT_ID=booking-e2e",
                "GOOGLE_CLIENT_SECRET=booking-e2e",
                "KAKAO_CLIENT_ID=booking-e2e",
                "KAKAO_CLIENT_SECRET=booking-e2e",
                "KAKAO_ADMIN_KEY=booking-e2e",
                "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback",
                "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/auth/callback"
        }
)
// Spring Boot 4에서 TestRestTemplate 빈은 RANDOM_PORT만으로 등록되지 않는다. 명시적으로 켠다.
@AutoConfigureTestRestTemplate
@Sql(scripts = {"/fixture/booking-e2e-reset.sql", "/fixture/booking-e2e-fixture.sql"})
@SuppressWarnings({"NonAsciiCharacters", "resource"})
public abstract class BookingE2ETestSupport {

    private static final int REDIS_PORT = 6379;

    /** fixture SQL이 쓰는 고정 ID 대역. LoadTestFixtureSeeder(910000000)와 겹치지 않는다. */
    protected static final long ID_BASE = 920000000L;
    protected static final long SHOW_ID = ID_BASE + 1;
    protected static final long PERFORMANCE_ID = ID_BASE + 1;
    protected static final List<Long> SEAT_IDS =
            List.of(ID_BASE + 1, ID_BASE + 2, ID_BASE + 3, ID_BASE + 4);
    protected static final int SEAT_PRICE = 120000;

    protected static final String SEAT_AVAILABLE = "AVAILABLE";
    protected static final String SEAT_OCCUPIED = "OCCUPIED";

    /**
     * JVM 하나에 컨테이너 하나를 쓴다. @Testcontainers의 @Container는 테스트 클래스마다 컨테이너를
     * 띄우고 클래스가 끝나면 멈추는데, Spring 컨텍스트는 클래스 사이에 재사용된다. 그러면 두 번째
     * 테스트 클래스가 이미 멈춘 컨테이너의 포트를 가리킨 컨텍스트를 그대로 물려받아 실패한다.
     * 정리는 Testcontainers의 Ryuk이 JVM 종료 시 맡는다.
     */
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 좌석 선택과 hold는 Redis에 남는다. DB만 되돌리면 이전 테스트의 점유가 다음 테스트의
     * 좌석 상태 조회에 그대로 보인다.
     */
    @BeforeEach
    void flushRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    // 인증 -----------------------------------------------------------------

    /**
     * 회원가입과 로그인을 실제 API로 수행해 access token을 얻는다.
     * 테스트가 JWT를 직접 발급하면 인증 경로가 검증 대상에서 빠진다.
     */
    protected String signUpAndLogin(final String email) {
        final String password = "password1234";
        final String name = email.substring(0, email.indexOf('@'));

        final ResponseEntity<JsonNode> signUp = restTemplate.postForEntity(
                "/api/v1/auth/signup",
                json(bodyOf(email, password, name)),
                JsonNode.class);
        if (!signUp.getStatusCode().is2xxSuccessful()) {
            fail("회원가입 실패: status=" + signUp.getStatusCode() + " body=" + signUp.getBody());
        }

        final ResponseEntity<JsonNode> login = restTemplate.postForEntity(
                "/api/v1/auth/login",
                json(bodyOf(email, password, null)),
                JsonNode.class);
        if (!login.getStatusCode().is2xxSuccessful()) {
            fail("로그인 실패: status=" + login.getStatusCode() + " body=" + login.getBody());
        }

        return requireData(login.getBody(), "로그인").get("accessToken").asText();
    }

    private String bodyOf(final String email, final String password, final String name) {
        if (name == null) {
            return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        }
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"name\":\"" + name + "\"}";
    }

    // HTTP 헬퍼 -------------------------------------------------------------

    protected HttpEntity<String> json(final String body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    protected HttpEntity<Void> authed(final String accessToken) {
        return new HttpEntity<>(authHeaders(accessToken));
    }

    protected HttpEntity<String> authedJson(final String accessToken, final String body) {
        final HttpHeaders headers = authHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpHeaders authHeaders(final String accessToken) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    protected ResponseEntity<JsonNode> get(final String uri, final String accessToken) {
        return restTemplate.exchange(uri, HttpMethod.GET, authed(accessToken), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> post(final String uri, final String accessToken) {
        return restTemplate.exchange(uri, HttpMethod.POST, authed(accessToken), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> delete(final String uri, final String accessToken) {
        return restTemplate.exchange(uri, HttpMethod.DELETE, authed(accessToken), JsonNode.class);
    }

    /** ApiResponse 봉투에서 data를 꺼낸다. 실패 응답이면 error를 그대로 드러내며 실패시킨다. */
    protected JsonNode requireData(final JsonNode body, final String what) {
        if (body == null || body.get("data") == null || body.get("data").isNull()) {
            fail(what + " 응답에 data가 없다: " + body);
        }
        return body.get("data");
    }

    // 도메인 조회 헬퍼 ------------------------------------------------------

    protected String seatStatus(final String accessToken, final long seatId) {
        final ResponseEntity<JsonNode> response =
                get("/api/v1/performances/" + PERFORMANCE_ID + "/seats/status", accessToken);
        final JsonNode seats = requireData(response.getBody(), "좌석 상태").get("seats");
        for (final JsonNode seat : seats) {
            // ticket-domain-module-redesign Phase 4 Task 10: 외부 판매 좌석 식별자는 이제
            // performanceSeatId다. 이 fixture는 performance_seats.id를 seats.id와 같은 값으로
            // 심어뒀으므로(920000001~) 물리 seatId 인자를 그대로 비교해도 맞는다.
            if (seat.get("performanceSeatId").asLong() == seatId) {
                return seat.get("status").asText();
            }
        }
        return fail("좌석 " + seatId + "를 좌석 상태 응답에서 찾지 못했다: " + seats);
    }

    protected String createOrderBody(final long seatId) {
        return "{\"performanceId\":" + PERFORMANCE_ID + ",\"seatIds\":[" + seatId + "]}";
    }

    // 비동기 대기 -----------------------------------------------------------

    /**
     * 커밋 후 처리는 요청 스레드 밖에서 끝난다. 고정 sleep은 느리거나 불안정하므로 조건을 폴링한다.
     */
    protected void pollUntil(final String what, final Duration timeout, final BooleanSupplier condition) {
        final Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleepBriefly();
        }
        fail(what + " 조건이 " + timeout + " 안에 만족되지 않았다");
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50L);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대기 중 인터럽트", interrupted);
        }
    }
}
