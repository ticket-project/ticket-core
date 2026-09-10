package com.ticket.booking.admission.exception.handler;

import com.ticket.booking.admission.exception.AdmissionTokenException;
import com.ticket.booking.admission.exception.AdmissionTokenExpiredException;
import com.ticket.booking.admission.exception.AdmissionTokenRequiredException;
import com.ticket.shared.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * admission 오류의 외부 계약(HTTP 상태, E-code, 공개 메시지)을 한곳에 고정한다.
 *
 * <p>admission 검증 실패 사유({@code reason})는 응답에 노출되지 않는다 — 로그에만 쓰인다. 이
 * 표가 곧 {@code gatling-test}가 참조하는 외부 계약이다.
 */
@SuppressWarnings("NonAsciiCharacters")
class AdmissionExceptionHandlerTest {

    private final AdmissionExceptionHandler handler = new AdmissionExceptionHandler();

    static Stream<Arguments> 오류_계약() {
        return Stream.of(
                Arguments.of(new AdmissionTokenRequiredException(), HttpStatus.FORBIDDEN, "E8000",
                        "대기열 입장 토큰이 필요합니다."),
                Arguments.of(new AdmissionTokenExpiredException("expired-reason", null), HttpStatus.FORBIDDEN, "E8001",
                        "대기열 입장 토큰이 만료되었습니다."),
                Arguments.of(new AdmissionTokenException("bad-signature"), HttpStatus.FORBIDDEN, "E8002",
                        "대기열 입장 토큰이 올바르지 않습니다."));
    }

    @ParameterizedTest
    @MethodSource("오류_계약")
    void admission_오류는_정해진_상태와_E_code와_메시지로_응답한다(
            final AdmissionTokenException exception,
            final HttpStatus expectedStatus,
            final String expectedCode,
            final String expectedMessage
    ) {
        final ResponseEntity<ApiResponse<Object>> response = handler.handleAdmissionTokenException(exception);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(expectedCode);
        assertThat(response.getBody().getError().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void 검증_실패_사유는_응답에_노출되지_않는다() {
        final AdmissionTokenException exception = new AdmissionTokenException("서명 불일치: 상세 진단 정보");

        final ResponseEntity<ApiResponse<Object>> response = handler.handleAdmissionTokenException(exception);

        assertThat(exception.getReason()).isEqualTo("서명 불일치: 상세 진단 정보");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError().getMessage()).doesNotContain("서명 불일치");
    }
}
