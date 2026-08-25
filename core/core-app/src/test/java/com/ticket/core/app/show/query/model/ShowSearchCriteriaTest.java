package com.ticket.core.app.show.query.model;

import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowSearchCriteriaTest {

    @Test
    void 시작일_From이_To보다_늦으면_INVALID_REQUEST_예외를_던진다() {
        assertThatThrownBy(() -> new ShowSearchCriteria(
                "공연",
                "CONCERT",
                null,
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1),
                null,
                null
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));
    }
}
