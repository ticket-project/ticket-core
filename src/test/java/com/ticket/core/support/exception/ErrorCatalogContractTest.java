package com.ticket.core.support.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전역 오류 카탈로그의 계약을 검사한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ErrorCatalogContractTest {

    @Test
    void 모든_오류는_상태와_코드와_메시지를_가진다() {
        for (final ErrorType error : ErrorType.values()) {
            assertThat(error.getStatus()).as("%s의 status", error).isNotNull();
            assertThat(error.getErrorCode()).as("%s의 errorCode", error).isNotNull();
            assertThat(error.getErrorCode().getCode()).as("%s의 code", error).isNotBlank();
            assertThat(error.getErrorCode().getDescription()).as("%s의 description", error).isNotBlank();
            assertThat(error.getMessage()).as("%s의 message", error).isNotBlank();
        }
    }

    @Test
    void 모든_오류_코드는_설명을_가지고_유일하다() {
        final List<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .toList();

        for (final ErrorCode code : ErrorCode.values()) {
            assertThat(code.getDescription()).as("%s의 description", code).isNotBlank();
        }
        assertThat(codes).doesNotHaveDuplicates();
    }
}
