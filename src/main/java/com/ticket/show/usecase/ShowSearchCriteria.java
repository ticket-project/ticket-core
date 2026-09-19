package com.ticket.show.usecase;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.venue.api.Region;

import lombok.Getter;

/**
 * {@code bookingStatus}는 HTTP query param 이름이라 {@link
 * com.ticket.show.endpoint.request.ShowSearchRequest}의 필드명은 그대로 두고, 이 계층의 타입만 {@link
 * SaleDisplayStatus}로 바꿨다(ADR 0007).
 */
@Getter
public class ShowSearchCriteria {
    private @Nullable String keyword;
    private @Nullable String category;
    private @Nullable SaleDisplayStatus saleDisplayStatus;
    private @Nullable LocalDate startDateFrom;
    private @Nullable LocalDate startDateTo;
    private @Nullable Region region;
    private @Nullable ShowCursor cursor;

    public ShowSearchCriteria(
            final @Nullable String keyword,
            final @Nullable String category,
            final @Nullable SaleDisplayStatus saleDisplayStatus,
            final @Nullable LocalDate startDateFrom,
            final @Nullable LocalDate startDateTo,
            final @Nullable Region region,
            final @Nullable ShowCursor cursor) {
        validateStartDateRange(startDateFrom, startDateTo);
        this.keyword = keyword;
        this.category = category;
        this.saleDisplayStatus = saleDisplayStatus;
        this.startDateFrom = startDateFrom;
        this.startDateTo = startDateTo;
        this.region = region;
        this.cursor = cursor;
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
                ShowListParam.parseRegion(region),
                cursor);
    }

    /** region 변환은 목록 조회와 같은 규칙을 써야 하므로 {@link ShowListParam#parseRegion}이 소유한다. */
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

    private void validateStartDateRange(
            final @Nullable LocalDate startDateFrom, final @Nullable LocalDate startDateTo) {
        if (startDateFrom == null || startDateTo == null) {
            return;
        }
        if (startDateFrom.isAfter(startDateTo)) {
            throw new InvalidRequestException("startDateFrom은 startDateTo보다 늦을 수 없습니다.");
        }
    }
}
