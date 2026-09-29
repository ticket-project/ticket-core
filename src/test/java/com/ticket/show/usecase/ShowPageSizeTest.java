package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ticket.shared.exception.InvalidRequestException;

@SuppressWarnings("NonAsciiCharacters")
class ShowPageSizeTest {
    @Test
    void 일부터_상한까지는_허용한다() {
        assertThatCode(() -> ShowPageSize.require(1)).doesNotThrowAnyException();
        assertThatCode(() -> ShowPageSize.require(ShowPageSize.MAX)).doesNotThrowAnyException();
    }

    @Test
    void 영_이하이거나_상한을_넘으면_잘못된_요청이다() {
        assertThatThrownBy(() -> ShowPageSize.require(0)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> ShowPageSize.require(ShowPageSize.MAX + 1))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("data", "size는 1 이상 100 이하여야 합니다.");
    }

    @Test
    void 상한이_없던_목록도_같은_상한을_쓴다() {
        assertThatThrownBy(() -> new GetSaleOpeningSoonShowsUseCase.Input("CONCERT", ShowPageSize.MAX + 1))
                .isInstanceOf(InvalidRequestException.class);
    }
}
