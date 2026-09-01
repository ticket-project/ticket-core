package com.ticket.core.support;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
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
 * {@link ApiControllerAdvice}가 만드는 오류 응답 계약을 고정한다.
 *
 * <p>RFC 9457 스타일의 공통 오류 표현 대신, 기존 {@code ApiResponse} 오류 envelope
 * ({@code result/data/error})와 E-code, HTTP 상태를 그대로 유지하는지 검증한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ApiControllerAdviceTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new ApiControllerAdvice())
            .build();

    @Test
    void 업무_충돌_오류는_정의된_상태와_E_code를_반환한다() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isConflict())
                .andExpect(contentTypeIsJson())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("E6000"))
                .andExpect(jsonPath("$.error.message").value("좌석이 이미 선점되었습니다."));
    }

    @Test
    void 미인증_오류는_401과_E1000을_반환한다() throws Exception {
        mockMvc.perform(get("/test/authentication-error"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E1000"));
    }

    @Test
    void 인가_실패는_403과_E1001을_반환한다() throws Exception {
        mockMvc.perform(get("/test/authorization-error"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("E1001"));
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
            throw new CoreException(ErrorType.SEAT_ALREADY_HOLD);
        }

        @GetMapping("/test/authentication-error")
        String authenticationError() {
            throw new CoreException(ErrorType.AUTHENTICATION_ERROR);
        }

        @GetMapping("/test/authorization-error")
        String authorizationError() {
            throw new CoreException(ErrorType.AUTHORIZATION_ERROR);
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new CoreException(ErrorType.NOT_FOUND_DATA);
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
