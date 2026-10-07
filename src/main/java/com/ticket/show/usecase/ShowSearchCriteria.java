package com.ticket.show.usecase;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.SaleDisplayStatus;

/**
 * {@code bookingStatus}는 HTTP query param 이름이라 {@link com.ticket.show.endpoint.request.ShowSearchRequest}의 필드명은 그대로 두고,
 * 이 계층의 타입만 {@link SaleDisplayStatus}로 바꿨다(ADR 0007).
 */
public record ShowSearchCriteria(
        @Nullable String keyword,
        @Nullable String category,
        @Nullable SaleDisplayStatus saleDisplayStatus,
        @Nullable LocalDate startDateFrom,
        @Nullable LocalDate startDateTo,
        @Nullable String region,
        @Nullable ShowCursor cursor) {
    public ShowSearchCriteria {
        if (startDateFrom != null && startDateTo != null && startDateFrom.isAfter(startDateTo)) {
            throw new InvalidRequestException("startDateFrom은 startDateTo보다 늦을 수 없습니다.");
        }
        region = ShowListParam.normalizeRegion(region);
    }

    /** API 경계에서 넘어온 문자열을 도메인 enum으로 바꾼다. 값이 올바르지 않으면 조회로 넘어가기 전에 INVALID_REQUEST로 끊는다. */
    public static ShowSearchCriteria of(
            final @Nullable String keyword,
            final @Nullable String category,
            final @Nullable String bookingStatus,
            final @Nullable LocalDate startDateFrom,
            final @Nullable LocalDate startDateTo,
            final @Nullable String region,
            final @Nullable ShowCursor cursor) {
        return new ShowSearchCriteria(
                keyword,
                category,
                parseEnum(SaleDisplayStatus.class, bookingStatus, "bookingStatus"),
                startDateFrom,
                startDateTo,
                region,
                cursor);
    }

    private static <E extends Enum<E>> @Nullable E parseEnum(
            final Class<E> type, final @Nullable String value, final String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (final IllegalArgumentException exception) {
            throw new InvalidRequestException(field + " 값이 올바르지 않습니다: " + value);
        }
    }
}
