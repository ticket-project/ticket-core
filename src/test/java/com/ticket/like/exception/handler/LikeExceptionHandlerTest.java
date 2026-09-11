package com.ticket.like.exception.handler;

import com.ticket.like.LikeType;
import com.ticket.like.exception.LikeAlreadyExistsException;
import com.ticket.shared.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * like 오류의 외부 계약(HTTP 상태, E-code, 공개 메시지)을 한곳에 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class LikeExceptionHandlerTest {

    private final LikeExceptionHandler handler = new LikeExceptionHandler();

    @Test
    void 이미_찜한_대상은_409와_E7001로_응답하고_data에_상세를_싣는다() {
        final LikeAlreadyExistsException exception =
                new LikeAlreadyExistsException(1L, LikeType.SHOW, 7L);

        final ResponseEntity<ApiResponse<Object>> response = handler.handleLikeException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("E7001");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("이미 찜한 대상입니다.");
        assertThat(response.getBody().getError().getData())
                .isEqualTo("이미 찜한 대상입니다. memberId=1, likeType=SHOW, targetId=7");
    }
}
