package com.ticket.web;

import com.ticket.shared.BusinessException;
import com.ticket.shared.BusinessProblem;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalProblemDetailHandler}가 {@link BusinessException}을 정확히 명시된 RFC 7807 wire
 * contract로 직렬화하는지 검증한다.
 *
 * <p>전체 Spring context 대신 이 handler 하나만 올리는 standalone MockMvc를 쓴다. 전체 context를
 * 올리면 기존 {@code GlobalExceptionHandler}도 {@code @RestControllerAdvice}로 함께 로드되어 두
 * 핸들러 사이의 advice 선택 순서에 테스트가 좌우된다.
 */
class GlobalProblemDetailHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SeatNotAvailableController())
            .setControllerAdvice(new GlobalProblemDetailHandler())
            .build();

    @Test
    void serializesBusinessExceptionAsProblemDetail() throws Exception {
        mockMvc.perform(get("/test/seat-not-available"))
                .andExpect(status().is(409))
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:ticket:problem:booking:seat-not-available"))
                .andExpect(jsonPath("$.title").value("좌석을 선택할 수 없습니다"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("이미 선택되었거나 판매된 좌석입니다"))
                .andExpect(jsonPath("$.code").value("BOOKING_SEAT_NOT_AVAILABLE"));
    }

    @RestController
    private static class SeatNotAvailableController {

        @GetMapping("/test/seat-not-available")
        String triggerSeatNotAvailable() {
            throw new BusinessException(SeatNotAvailableProblem.INSTANCE);
        }
    }

    private enum SeatNotAvailableProblem implements BusinessProblem {
        INSTANCE;

        @Override
        public String module() {
            return "booking";
        }

        @Override
        public String code() {
            return "seat-not-available";
        }

        @Override
        public String title() {
            return "좌석을 선택할 수 없습니다";
        }

        @Override
        public int status() {
            return 409;
        }

        @Override
        public String detail() {
            return "이미 선택되었거나 판매된 좌석입니다";
        }
    }
}
