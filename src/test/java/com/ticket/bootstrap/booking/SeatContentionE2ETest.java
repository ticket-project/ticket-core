package com.ticket.bootstrap.booking;

import com.ticket.bootstrap.support.BookingE2ETestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 좌석에 동시에 주문이 들어와도 하나만 성공하는지 실제 스택에서 확인한다.
 *
 * <p>티켓 예매에서 이중 판매보다 큰 사고가 없는데, 이 성질을 실제 분산락과 실제 Redis로 검증하는
 * 테스트가 없었다. 단위 테스트는 LockManager를 mock으로 바꾸므로 락이 실제로 상호 배제하는지
 * 알 수 없고, 락 범위가 잘못 잡혀도 통과한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeatContentionE2ETest extends BookingE2ETestSupport {

    /**
     * 한 번만 돌리면 두 요청이 실제로 겹치지 않은 채 통과할 수 있다. 여러 판을 돌려
     * 경합이 최소 한 번은 실제로 일어나게 한다.
     */
    private static final int ROUNDS = 4;
    private static final int CONTENDERS = 2;

    @Test
    void 같은_좌석에_동시_주문이_들어와도_하나만_성공한다() throws Exception {
        final List<String> tokens = new ArrayList<>();
        for (int contender = 0; contender < CONTENDERS; contender++) {
            tokens.add(signUpAndLogin("contender" + contender + "@e2e.test"));
        }

        // 판마다 다른 좌석을 쓴다. 앞 판의 hold가 남아 다음 판을 오염시키지 않는다.
        for (int round = 0; round < ROUNDS; round++) {
            final long seatId = SEAT_IDS.get(round % SEAT_IDS.size());
            assertExactlyOneWins(tokens, seatId, round);
        }
    }

    private void assertExactlyOneWins(final List<String> tokens, final long seatId, final int round)
            throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
        final CountDownLatch startLine = new CountDownLatch(1);
        try {
            final List<Future<ResponseEntity<JsonNode>>> results = new ArrayList<>();
            for (final String token : tokens) {
                results.add(pool.submit(createOrderAt(startLine, token, seatId)));
            }

            // 모든 스레드를 같은 순간에 출발시킨다. 순차 실행이면 경합이 일어나지 않는다.
            startLine.countDown();

            int created = 0;
            String winnerToken = null;
            String winnerOrderKey = null;
            final List<String> rejections = new ArrayList<>();
            for (int index = 0; index < results.size(); index++) {
                final ResponseEntity<JsonNode> response = results.get(index).get(30, TimeUnit.SECONDS);
                if (response.getStatusCode() == HttpStatus.CREATED) {
                    created++;
                    winnerToken = tokens.get(index);
                    winnerOrderKey = requireData(response.getBody(), "주문 생성").get("orderKey").asText();
                } else {
                    rejections.add(response.getStatusCode() + " " + response.getBody());
                }
            }

            assertThat(created)
                    .as("%d판: 같은 좌석 %d에 동시 주문 %d건 중 성공이 정확히 하나여야 한다. 거절된 응답=%s",
                            round, seatId, tokens.size(), rejections)
                    .isEqualTo(1);

            // 회차당 같은 회원은 PENDING 주문을 하나만 가질 수 있다. 이긴 주문을 취소하지 않으면
            // 다음 판에서 그 회원이 좌석 경합이 아니라 중복 주문으로 거절되어 검증이 무의미해진다.
            cancel(winnerToken, winnerOrderKey);
        } finally {
            pool.shutdownNow();
        }
    }

    private void cancel(final String token, final String orderKey) {
        final ResponseEntity<JsonNode> response = delete("/api/v1/orders/" + orderKey, token);
        assertThat(response.getStatusCode())
                .as("판 정리를 위한 주문 취소가 실패했다: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    private Callable<ResponseEntity<JsonNode>> createOrderAt(
            final CountDownLatch startLine,
            final String token,
            final long seatId
    ) {
        return () -> {
            startLine.await();
            return restTemplate.exchange(
                    "/api/v1/orders", HttpMethod.POST,
                    authedJson(token, createOrderBody(seatId)), JsonNode.class);
        };
    }
}
