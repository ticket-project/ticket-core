package com.ticket.core.app.show.query.model;

import com.ticket.core.domain.show.BookingStatus;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class ShowSearchCriteria {
    private String keyword;
    private String category;
    private BookingStatus bookingStatus;
    private LocalDate startDateFrom;
    private LocalDate startDateTo;
    private Region region;
    private String cursor;

    public ShowSearchCriteria(
            final String keyword,
            final String category,
            final BookingStatus bookingStatus,
            final LocalDate startDateFrom,
            final LocalDate startDateTo,
            final Region region,
            final String cursor
    ) {
        validateStartDateRange(startDateFrom, startDateTo);
        this.keyword = keyword;
        this.category = category;
        this.bookingStatus = bookingStatus;
        this.startDateFrom = startDateFrom;
        this.startDateTo = startDateTo;
        this.region = region;
        this.cursor = cursor;
    }

    private void validateStartDateRange(final LocalDate startDateFrom, final LocalDate startDateTo) {
        if (startDateFrom == null || startDateTo == null) {
            return;
        }
        if (startDateFrom.isAfter(startDateTo)) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "startDateFrom은 startDateTo보다 늦을 수 없습니다.");
        }
    }
}
