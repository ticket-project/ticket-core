package com.ticket.show.exception.handler;

import com.ticket.show.exception.UnsupportedShowSortException;
import com.ticket.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * show 오류의 외부 계약(HTTP 상태, E-code, 공개 메시지)을 한곳에 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ShowExceptionHandlerTest {

    private final ShowExceptionHandler handler = new ShowExceptionHandler();

    @Test
    void 미지원_정렬_조건은_400과_E7002로_응답하고_전달된_값을_data에_싣는다() {
        final UnsupportedShowSortException exception = new UnsupportedShowSortException("UNKNOWN_SORT");

        final ResponseEntity<ApiResponse<Object>> response = handler.handleShowException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("E7002");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("지원하지 않는 정렬 조건입니다.");
        assertThat(response.getBody().getError().getData()).isEqualTo("UNKNOWN_SORT");
    }
}
