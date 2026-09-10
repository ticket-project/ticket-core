package com.ticket.shared.exception.handler;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler}가 만드는 오류 응답 계약을 고정한다.
 *
 * <p>RFC 9457 스타일의 공통 오류 표현 대신, 기존 {@code ApiResponse} 오류 envelope
 * ({@code result/data/error})와 E-code, HTTP 상태를 그대로 유지하는지 검증한다.
 *
 * <p>업무 오류의 HTTP 상태는 더 이상 예외 자신이 들고 있지 않고 그 오류를 잡는 handler(각 module
 * handler, 그리고 여기 있는 공통 오류 셋)가 안다 — {@link TicketException}은 errorCode·message·
 * data만 옮기는 그릇이다. {@code InvalidRequestException}으로 "예외의 data가 error.data로 나가고
 * message를 덮지 않는다"는 공통 불변식을 확인한다. module 고유 오류(E6000 등)의 상태·코드·메시지는
 * 각 module의 handler 테스트가 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void 공통_오류는_예외가_들고_있는_code와_message를_그대로_반환하고_handler가_상태를_정한다() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(contentTypeIsJson())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("E400"))
                .andExpect(jsonPath("$.error.message").value("요청이 올바르지 않습니다."))
                .andExpect(jsonPath("$.error.data").doesNotExist());
    }

    @Test
    void 업무_오류의_data는_error_data로_나가고_message를_덮지_않는다() throws Exception {
        mockMvc.perform(get("/test/business-error-with-data"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E400"))
                .andExpect(jsonPath("$.error.message").value("요청이 올바르지 않습니다."))
                .andExpect(jsonPath("$.error.data").value("seatId=7"));
    }

    @Test
    void 데이터를_찾을_수_없으면_404와_E404를_반환한다() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E404"));
    }

    @Test
    void 존재하지_않는_API는_404와_E404를_반환한다() throws Exception {
        mockMvc.perform(get("/test/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E404"));
    }

    @Test
    void bean_validation_실패는_400과_E400을_반환한다() throws Exception {
        mockMvc.perform(post("/test/validated-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E400"));
    }

    @Test
    void 읽을_수_없는_json은_400과_E400을_반환한다() throws Exception {
        mockMvc.perform(post("/test/validated-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E400"));
    }

    @Test
    void method_parameter_validation_실패는_400과_E400을_반환한다() throws Exception {
        mockMvc.perform(get("/test/validated-param").param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E400"));
    }

    @Test
    void 예상하지_못한_예외는_500과_E500을_반환하고_내부_정보를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("E500"))
                .andExpect(jsonPath("$.error.message").value("일시적인 오류가 발생했습니다."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-internal-detail"))));
    }

    private static org.springframework.test.web.servlet.ResultMatcher contentTypeIsJson() {
        return content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }

    @RestController
    private static class TestController {

        @GetMapping("/test/business-error")
        String businessError() {
            throw new InvalidRequestException();
        }

        @GetMapping("/test/business-error-with-data")
        String businessErrorWithData() {
            throw new InvalidRequestException("seatId=7");
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new NotFoundException();
        }

        @PostMapping("/test/validated-body")
        String validatedBody(@RequestBody @jakarta.validation.Valid ValidatedRequest request) {
            return "ok";
        }

        @Validated
        @GetMapping("/test/validated-param")
        String validatedParam(@RequestParam @NotBlank String name) {
            return "ok";
        }

        @GetMapping("/test/unexpected-error")
        String unexpectedError() {
            throw new IllegalStateException("secret-internal-detail");
        }
    }

    private static class ValidatedRequest {

        @NotBlank
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
