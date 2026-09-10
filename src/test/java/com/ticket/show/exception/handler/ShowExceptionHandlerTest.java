package com.ticket.show.exception.handler;

import com.ticket.show.catalog.application.ShowSort;
import com.ticket.show.catalog.exception.UnsupportedShowSortException;
import com.ticket.shared.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * show 오류의 외부 계약(HTTP 상태, E-code, 공개 메시지)을 한곳에 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ShowExceptionHandlerTest {

    private final ShowExceptionHandler handler = new ShowExceptionHandler();

    /**
     * 예외를 직접 만들지 않고 실제 파싱 경로에서 받는다. 상세 문구를 만드는 곳이 예외 안으로
     * 옮겨졌으므로, 접두어가 정확히 한 번만 붙는지는 이 경로로만 확인할 수 있다.
     */
    @Test
    void 미지원_정렬_조건은_400과_E7002로_응답하고_원문에_접두어를_한_번_붙여_data에_싣는다() {
        final UnsupportedShowSortException exception = catchThrowableOfType(
                () -> ShowSort.from("UNKNOWN_SORT"), UnsupportedShowSortException.class);

        final ResponseEntity<ApiResponse<Object>> response = handler.handleShowException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("E7002");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("지원하지 않는 정렬 조건입니다.");
        assertThat(response.getBody().getError().getData()).isEqualTo("지원하지 않는 sort: UNKNOWN_SORT");
    }
}
