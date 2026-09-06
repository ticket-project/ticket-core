package com.ticket.error.handler;

import com.ticket.error.ErrorCode;
import com.ticket.error.NotFoundException;
import com.ticket.error.TicketException;
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
 * <p><b>업무 오류는 이 파일이 소유한 fixture로 검증한다.</b> 예전에는 booking의 E6000과 member의
 * E1000/E1001을 빌려 와 "예외가 들고 있는 상태·code·message를 그대로 직렬화한다"는 한 가지 동작을
 * 세 번 확인했다. 그 code들은 이제 각 module의 계약이고 각 module의 handler 테스트와 controller
 * 계약 테스트가 고정하므로, 여기서는 어느 module에도 속하지 않는 {@link TestErrorCode}로 확인한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void 업무_오류는_예외가_들고_있는_상태와_E_code와_message를_그대로_반환한다() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isConflict())
                .andExpect(contentTypeIsJson())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("E9001"))
                .andExpect(jsonPath("$.error.message").value("테스트 업무 충돌입니다."))
                .andExpect(jsonPath("$.error.data").doesNotExist());
    }

    @Test
    void 업무_오류의_data는_error_data로_나가고_message를_덮지_않는다() throws Exception {
        mockMvc.perform(get("/test/business-error-with-data"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("E9001"))
                .andExpect(jsonPath("$.error.message").value("테스트 업무 충돌입니다."))
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
            throw new TestBusinessException(null);
        }

        @GetMapping("/test/business-error-with-data")
        String businessErrorWithData() {
            throw new TestBusinessException("seatId=7");
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

    /** 어느 module에도 속하지 않는 test 전용 code다. 실제 카탈로그와 겹치지 않는 대역을 쓴다. */
    private enum TestErrorCode implements ErrorCode {

        E9001("테스트 업무 충돌");

        private final String description;

        TestErrorCode(final String description) {
            this.description = description;
        }

        @Override
        public String getCode() {
            return name();
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    private static final class TestBusinessException extends TicketException {

        private TestBusinessException(final Object data) {
            super(org.springframework.http.HttpStatus.CONFLICT, TestErrorCode.E9001, "테스트 업무 충돌입니다.", data);
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
