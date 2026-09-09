package com.ticket.show.application;

import com.ticket.venue.Region;
import com.ticket.error.InvalidRequestException;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 판매 오픈 예정 공연 목록 조회 조건이다. 커서는 HTTP 문자열이 아니라 타입 값으로 받는다.
 *
 * <p>필드 이름은 {@code display} 어휘를 쓴다 — {@link com.ticket.show.web.request.SaleOpeningSoonRequest}의
 * {@code saleStartDateFrom} 등 query param 이름은 HTTP 계약이라 바꾸지 않았고,
 * {@code toParam()}이 위치 인자로 이 타입에 넘기므로 필드 이름이 달라도 무관하다.
 */
@Getter
public class SaleOpeningSoonSearchParam {

    private final String category;

    private final String title;

    private final Region region;

    private final LocalDateTime displaySaleStartsAtFrom;

    private final LocalDateTime displaySaleStartsAtTo;

    private final LocalDateTime displaySaleEndsAtFrom;

    private final LocalDateTime displaySaleEndsAtTo;

    private final ShowCursor cursor;

    public SaleOpeningSoonSearchParam(
            final String category,
            final String title,
            final Region region,
            final LocalDateTime displaySaleStartsAtFrom,
            final LocalDateTime displaySaleStartsAtTo,
            final LocalDateTime displaySaleEndsAtFrom,
            final LocalDateTime displaySaleEndsAtTo,
            final ShowCursor cursor
    ) {
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
            final String category,
            final String title,
            final String region,
            final LocalDateTime displaySaleStartsAtFrom,
            final LocalDateTime displaySaleStartsAtTo,
            final LocalDateTime displaySaleEndsAtFrom,
            final LocalDateTime displaySaleEndsAtTo,
            final ShowCursor cursor
    ) {
        return new SaleOpeningSoonSearchParam(
                category,
                title,
                ShowParam.parseRegion(region),
                displaySaleStartsAtFrom,
                displaySaleStartsAtTo,
                displaySaleEndsAtFrom,
                displaySaleEndsAtTo,
                cursor
        );
    }

    /**
     * 한쪽만 주면 열린 구간이다. 둘 다 주면 from이 to보다 늦을 수 없다.
     * ShowSearchCriteria의 startDate 범위 판정과 같은 규칙이다.
     */
    private static void validateRange(
            final LocalDateTime from,
            final LocalDateTime to,
            final String field
    ) {
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            throw new InvalidRequestException(
                                        field + "From은 " + field + "To보다 늦을 수 없습니다."
            );
        }
    }
}
