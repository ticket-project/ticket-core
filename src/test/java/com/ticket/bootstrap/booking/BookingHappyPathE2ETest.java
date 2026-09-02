package com.ticket.bootstrap.booking;

import tools.jackson.databind.JsonNode;
import com.ticket.bootstrap.support.BookingE2ETestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좌석 조회부터 주문 취소까지를 실제 HTTP로 한 번 관통한다.
 *
 * <p>각 단계에서 응답만 보지 않고 좌석 상태를 다시 조회한다. 좌석 상태는 DB 상태와 Redis 점유를
 * 합쳐 계산하므로(GetSeatStatusUseCase), 여기서 어긋나면 층 사이 연결이 깨진 것이다. 단위
 * 테스트는 각 층을 따로 보기 때문에 이 어긋남을 통과시킨다.
 */
@SuppressWarnings("NonAsciiCharacters")
class BookingHappyPathE2ETest extends BookingE2ETestSupport {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void 좌석을_고르고_주문했다가_취소하면_좌석이_돌아온다() {
        final String token = signUpAndLogin("happy-path@e2e.test");
        final long seatId = SEAT_IDS.get(0);

        // 1. 시작 상태: fixture의 좌석 넷이 모두 판매 가능하다.
        assertThat(seatStatus(token, seatId)).isEqualTo(SEAT_AVAILABLE);
        assertThat(seatStatus(token, SEAT_IDS.get(1))).isEqualTo(SEAT_AVAILABLE);

        // 2. 좌석 선택. Redis에만 남는 임시 상태이고 DB는 바뀌지 않는다.
        final ResponseEntity<JsonNode> select = post(
                "/api/v1/performances/" + PERFORMANCE_ID + "/seats/" + seatId + "/select", token);
        assertThat(select.getStatusCode())
                .as("좌석 선택 응답: %s", select.getBody())
                .isEqualTo(HttpStatus.OK);

        // 선택은 좌석 상태 조회에 점유로 보여야 한다. DB가 아니라 Redis에서 온 값이다.
        assertThat(seatStatus(token, seatId))
                .as("선택한 좌석이 점유로 보이지 않는다. 좌석 선택 Redis 키와 조회 쪽 키가 어긋났을 수 있다")
                .isEqualTo(SEAT_OCCUPIED);
        assertThat(seatStatus(token, SEAT_IDS.get(1)))
                .as("고르지 않은 좌석까지 점유로 보인다")
                .isEqualTo(SEAT_AVAILABLE);

        // 3. 주문 생성. 여기서 Redis hold와 DB PENDING 주문이 한 흐름으로 만들어진다.
        final ResponseEntity<JsonNode> create = restTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                authedJson(token, createOrderBody(seatId)), JsonNode.class);
        assertThat(create.getStatusCode())
                .as("주문 생성 응답: %s", create.getBody())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(create.getHeaders().getFirst("X-Order-Key"))
                .as("주문 식별자를 헤더로 돌려주지 않는다")
                .isNotBlank();

        final String orderKey = requireData(create.getBody(), "주문 생성").get("orderKey").asText();
        assertThat(orderKey).isNotBlank();

        // 4. 주문 상세가 실제로 조회된다. 커밋이 끝났다는 뜻이다.
        final ResponseEntity<JsonNode> detail = get("/api/v1/orders/" + orderKey, token);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        final JsonNode order = requireData(detail.getBody(), "주문 상세");
        assertThat(order.get("status").asText()).isEqualTo("PENDING");

        // 5. 주문이 잡은 좌석은 hold로 계속 점유 상태여야 한다.
        //    선택 해제와 HELD 전파는 커밋 후 background에서 끝나므로 폴링으로 기다린다.
        pollUntil("주문한 좌석이 hold로 점유 유지", ASYNC_TIMEOUT,
                () -> SEAT_OCCUPIED.equals(seatStatus(token, seatId)));
        assertThat(seatStatus(token, SEAT_IDS.get(1)))
                .as("주문하지 않은 좌석까지 점유됐다")
                .isEqualTo(SEAT_AVAILABLE);

        // 6. 취소.
        final ResponseEntity<JsonNode> cancel = delete("/api/v1/orders/" + orderKey, token);
        assertThat(cancel.getStatusCode())
                .as("주문 취소 응답: %s", cancel.getBody())
                .isEqualTo(HttpStatus.OK);

        final ResponseEntity<JsonNode> afterCancel = get("/api/v1/orders/" + orderKey, token);
        assertThat(requireData(afterCancel.getBody(), "취소 후 주문").get("status").asText())
                .isEqualTo("CANCELED");

        // 7. 취소하면 좌석이 다시 팔 수 있는 상태로 돌아와야 한다.
        //    hold 해제는 커밋 뒤 비동기 event listener 경로를 타므로 여기도 폴링한다.
        pollUntil("취소한 좌석이 판매 가능으로 복귀", ASYNC_TIMEOUT,
                () -> SEAT_AVAILABLE.equals(seatStatus(token, seatId)));
    }

    /**
     * 선택 없이도 주문할 수 있어야 한다. selection은 UX 보조 상태이고 hold의 선행 조건이 아니다
     * (docs/adr/0001-selection-and-hold-are-independent.md).
     */
    @Test
    void 좌석을_고르지_않아도_주문할_수_있다() {
        final String token = signUpAndLogin("no-selection@e2e.test");
        final long seatId = SEAT_IDS.get(2);

        final ResponseEntity<JsonNode> create = restTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                authedJson(token, createOrderBody(seatId)), JsonNode.class);

        assertThat(create.getStatusCode())
                .as("선택 없이 주문이 거부됐다. ADR 0001과 어긋난다. 응답: %s", create.getBody())
                .isEqualTo(HttpStatus.CREATED);

        pollUntil("주문한 좌석이 점유로 보임", ASYNC_TIMEOUT,
                () -> SEAT_OCCUPIED.equals(seatStatus(token, seatId)));
    }
}
