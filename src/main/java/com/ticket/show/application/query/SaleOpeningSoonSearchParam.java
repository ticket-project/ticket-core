package com.ticket.show.application.query;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.venue.api.Region;

import lombok.Getter;

/**
 * 판매 오픈 예정 공연 목록 조회 조건이다. 커서는 HTTP 문자열이 아니라 타입 값으로 받는다.
 *
 * <p>필드 이름은 {@code display} 어휘를 쓴다 — {@link
 * com.ticket.show.endpoint.request.SaleOpeningSoonRequest}의 {@code saleStartDateFrom} 등 query param
 * 이름은 HTTP 계약이라 바꾸지 않았고, {@code toParam()}이 위치 인자로 이 타입에 넘기므로 필드 이름이 달라도 무관하다.
 */
@Getter
public class SaleOpeningSoonSearchParam {
    private final @Nullable String category;
    private final @Nullable String title;
    private final @Nullable Region region;
    private final @Nullable LocalDateTime displaySaleStartsAtFrom;
    private final @Nullable LocalDateTime displaySaleStartsAtTo;
    private final @Nullable LocalDateTime displaySaleEndsAtFrom;
    private final @Nullable LocalDateTime displaySaleEndsAtTo;
    private final @Nullable ShowCursor cursor;

    public SaleOpeningSoonSearchParam(
            final @Nullable String category,
            final @Nullable String title,
            final @Nullable Region region,
            final @Nullable LocalDateTime displaySaleStartsAtFrom,
            final @Nullable LocalDateTime displaySaleStartsAtTo,
            final @Nullable LocalDateTime displaySaleEndsAtFrom,
            final @Nullable LocalDateTime displaySaleEndsAtTo,
            final @Nullable ShowCursor cursor) {
        validateRange(displaySaleStartsAtFrom, displaySaleStartsAtTo, "displaySaleStartsAt");
        validateRange(displaySaleEndsAtFrom, displaySaleEndsAtTo, "displaySaleEndsAt");
        this.category = category;
        this.title = title;
        this.region = region;
        this.displaySaleStartsAtFrom = displaySaleStartsAtFrom;
        this.displaySaleStartsAtTo = displaySaleStartsAtTo;
        this.displaySaleEndsAtFrom = displaySaleEndsAtFrom;
        this.displaySaleEndsAtTo = displaySaleEndsAtTo;
        this.cursor = cursor;
    }

    public static SaleOpeningSoonSearchParam of(
            final @Nullable String category,
            final @Nullable String title,
            final @Nullable String region,
            final @Nullable LocalDateTime displaySaleStartsAtFrom,
            final @Nullable LocalDateTime displaySaleStartsAtTo,
            final @Nullable LocalDateTime displaySaleEndsAtFrom,
            final @Nullable LocalDateTime displaySaleEndsAtTo,
            final @Nullable ShowCursor cursor) {
        return new SaleOpeningSoonSearchParam(
                category,
                title,
                ShowListParam.parseRegion(region),
                displaySaleStartsAtFrom,
                displaySaleStartsAtTo,
                displaySaleEndsAtFrom,
                displaySaleEndsAtTo,
                cursor);
    }

    /** 한쪽만 주면 열린 구간이다. 둘 다 주면 from이 to보다 늦을 수 없다. ShowSearchCriteria의 startDate 범위 판정과 같은 규칙이다. */
    private static void validateRange(
            final @Nullable LocalDateTime from,
            final @Nullable LocalDateTime to,
            final String field) {
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            throw new InvalidRequestException(field + "From은 " + field + "To보다 늦을 수 없습니다.");
        }
    }
}
