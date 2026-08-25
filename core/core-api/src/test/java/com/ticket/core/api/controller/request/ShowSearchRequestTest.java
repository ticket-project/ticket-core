package com.ticket.core.api.controller.request;

import com.ticket.core.domain.show.BookingStatus;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowSearchRequestTest {

    @Test
    void 요청값을_domain_검색조건으로_변환한다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬",
                "MUSICAL",
                "ON_SALE",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "SEOUL",
                "cursor-1"
        );

        ShowSearchCriteria criteria = request.toCriteria();

        assertThat(criteria.getKeyword()).isEqualTo("뮤지컬");
        assertThat(criteria.getCategory()).isEqualTo("MUSICAL");
        assertThat(criteria.getBookingStatus()).isEqualTo(BookingStatus.ON_SALE);
        assertThat(criteria.getStartDateFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(criteria.getStartDateTo()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(criteria.getRegion()).isEqualTo(Region.SEOUL);
        assertThat(criteria.getCursor()).isEqualTo("cursor-1");
    }

    @Test
    void 알_수_없는_region_문자열이면_invalid_request_예외를_던진다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬",
                "MUSICAL",
                "ON_SALE",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "NOWHERE",
                "cursor-1"
        );

        assertThatThrownBy(request::toCriteria)
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void 시작일_From이_To보다_늦으면_예외를_던진다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬",
                "MUSICAL",
                "ON_SALE",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1),
                "SEOUL",
                null
        );

        assertThatThrownBy(request::toCriteria)
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));
    }
}
