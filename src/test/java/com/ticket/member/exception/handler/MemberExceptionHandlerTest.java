package com.ticket.member.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.ticket.member.exception.DuplicateEmailException;
import com.ticket.shared.web.ApiResponse;

/** member 중복 이메일 오류의 외부 계약을 고정한다. */
@SuppressWarnings("NonAsciiCharacters")
class MemberExceptionHandlerTest {
    private final MemberExceptionHandler handler = new MemberExceptionHandler();

    @Test
    void 중복_이메일은_기존_상태와_코드로_응답한다() {
        final ResponseEntity<ApiResponse<Object>> response =
                handler.handleMemberException(new DuplicateEmailException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("E2000");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("중복된 이메일은 불가능합니다.");
        assertThat(response.getBody().getData()).isNull();
    }
}
