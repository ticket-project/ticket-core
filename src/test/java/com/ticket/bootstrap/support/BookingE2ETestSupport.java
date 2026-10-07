package com.ticket.bootstrap.support;

import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.api.SocialProvider;
import com.ticket.security.jwt.JwtAuthTokenIssuer;
import com.ticket.testsupport.CoreApplicationTestSupport;

import tools.jackson.databind.JsonNode;

/**
 * 실제 스택을 관통하는 통합 테스트의 베이스다.
 *
 * <p>단위 테스트는 계층마다 mock을 끼우므로 각 층이 자기 mock에 대해 맞으면 통과한다. 층 사이를 이어 붙였을 때 어긋나는 것(Redis key 불일치, 커밋과 커밋 후 처리의 순서, 트랜잭션 경계)은
 * 진짜 HTTP로 진짜 스택을 두드려야 드러난다.
 *
 * <p>H2·Redis·기동 설정은 {@link CoreApplicationTestSupport}가 소유한다. 이 클래스는 실제 HTTP 호출과 fixture를 더한다.
 *
 * <p>worker.enabled는 기본값(true)을 그대로 둔다. 끄면 하위 클래스마다 프로퍼티를 재정의해야 해서 Spring 컨텍스트가 갈라지고, 스케줄러 주기가 5분과 2분이라 초 단위로 끝나는 테스트를
 * 방해하지 않는다. 대신 fixture의 hold_time을 넉넉히 두어 만료가 끼어들지 않게 한다.
 */
// Spring Boot 4에서 TestRestTemplate 빈은 RANDOM_PORT만으로 등록되지 않는다. 명시적으로 켠다.
@AutoConfigureTestRestTemplate
@Sql(scripts = {"/fixture/booking-e2e-reset.sql", "/fixture/booking-e2e-fixture.sql"})
@SuppressWarnings("NonAsciiCharacters")
public abstract class BookingE2ETestSupport extends CoreApplicationTestSupport {

    /** fixture SQL이 쓰는 고정 ID 대역. seed/의 부하 테스트 전용 대역(910000000)과 겹치지 않는다. */
    protected static final long ID_BASE = 920000000L;

    protected static final long SHOW_ID = ID_BASE + 1;
    protected static final long PERFORMANCE_ID = ID_BASE + 1;
    protected static final List<Long> SEAT_IDS = List.of(ID_BASE + 1, ID_BASE + 2, ID_BASE + 3, ID_BASE + 4);
    protected static final int SEAT_PRICE = 120000;

    protected static final String SEAT_AVAILABLE = "AVAILABLE";
    protected static final String SEAT_OCCUPIED = "OCCUPIED";

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private MemberAccountApi memberAccountApi;

    @Autowired
    private JwtAuthTokenIssuer authTokenIssuer;

    /** 좌석 선택과 hold는 Redis에 남는다. DB만 되돌리면 이전 테스트의 점유가 다음 테스트의 좌석 상태 조회에 그대로 보인다. */
    @BeforeEach
    void flushRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    // 인증 -----------------------------------------------------------------

    /**
     * 새 소셜 회원을 만들고 앱의 토큰 발급기로 access token을 얻는다. 회원 가입·로그인은 OAuth2 provider 왕복이 필요해 E2E가 재현할 수 없으므로, 그 끝에서 부르는 member
     * 공개 계약과 토큰 발급기를 그대로 쓴다. 발급된 토큰은 실제 인증 필터가 검증한다.
     */
    protected String loginAsNewMember(final String email) {
        final String name = email.substring(0, email.indexOf('@'));
        final AuthenticatedMember member = memberAccountApi.resolveSocialAccount(
                new SocialIdentity(SocialProvider.GOOGLE, "e2e-" + email, email, true, name));
        return authTokenIssuer.issueTokens(member.memberId(), member.role()).accessToken();
    }

    // HTTP 헬퍼 -------------------------------------------------------------

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
            if (seat.get("seatId").asLong() == seatId) {
                return seat.get("status").asText();
            }
        }
        return fail("좌석 " + seatId + "를 좌석 상태 응답에서 찾지 못했다: " + seats);
    }

    protected String createOrderBody(final long seatId) {
        return "{\"performanceId\":" + PERFORMANCE_ID + ",\"seatIds\":[" + seatId + "]}";
    }

    // 비동기 대기 -----------------------------------------------------------

    /** 커밋 후 처리는 요청 스레드 밖에서 끝난다. 고정 sleep은 느리거나 불안정하므로 조건을 폴링한다. */
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
