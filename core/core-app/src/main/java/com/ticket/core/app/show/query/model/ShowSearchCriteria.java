package com.ticket.core.app.show.query.model;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.show.BookingStatus;
import com.ticket.core.domain.show.meta.Region;
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
    private ShowCursor cursor;

    public ShowSearchCriteria(
            final String keyword,
            final String category,
            final BookingStatus bookingStatus,
            final LocalDate startDateFrom,
            final LocalDate startDateTo,
            final Region region,
            final ShowCursor cursor
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

    /**
     * API 경계에서 넘어온 문자열을 도메인 enum으로 바꾼다. 값이 올바르지 않으면
     * 조회로 넘어가기 전에 INVALID_REQUEST로 끊는다.
     */
    public static ShowSearchCriteria of(
            final String keyword,
            final String category,
            final String bookingStatus,
            final LocalDate startDateFrom,
            final LocalDate startDateTo,
            final String region,
            final ShowCursor cursor
    ) {
        return new ShowSearchCriteria(
                keyword,
                category,
                parseEnum(BookingStatus.class, bookingStatus, "bookingStatus"),
                startDateFrom,
                startDateTo,
                ShowParam.parseRegion(region),
                cursor
        );
    }

    /**
     * region 변환은 목록 조회와 같은 규칙을 써야 하므로 {@link ShowParam#parseRegion}이 소유한다.
     */
    private static <E extends Enum<E>> E parseEnum(
            final Class<E> type,
            final String value,
            final String field
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (final IllegalArgumentException exception) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, field + " 값이 올바르지 않습니다: " + value);
        }
    }

    private void validateStartDateRange(final LocalDate startDateFrom, final LocalDate startDateTo) {
        if (startDateFrom == null || startDateTo == null) {
            return;
        }
        if (startDateFrom.isAfter(startDateTo)) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "startDateFrom은 startDateTo보다 늦을 수 없습니다.");
        }
    }
}
