package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.ticket.shared.exception.InvalidRequestException;

@SuppressWarnings("NonAsciiCharacters")
class ShowSearchCriteriaTest {
    @Test
    void 시작일_From이_To보다_늦으면_INVALID_REQUEST_예외를_던진다() {
        assertThatThrownBy(() -> new ShowSearchCriteria(
                        "공연", "CONCERT", null, LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1), null, null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
