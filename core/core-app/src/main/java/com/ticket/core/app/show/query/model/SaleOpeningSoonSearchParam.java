package com.ticket.core.app.show.query.model;

import com.ticket.core.domain.show.meta.Region;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 판매 오픈 예정 공연 목록 조회 조건이다. 커서는 HTTP 문자열이 아니라 타입 값으로 받는다.
 */
@Getter
public class SaleOpeningSoonSearchParam {

    private final String category;

    private final String title;

    private final Region region;

    private final LocalDateTime saleStartDateFrom;

    private final LocalDateTime saleStartDateTo;

    private final LocalDateTime saleEndDateFrom;

    private final LocalDateTime saleEndDateTo;

    private final ShowCursor cursor;

    public SaleOpeningSoonSearchParam(
            final String category,
            final String title,
            final Region region,
            final LocalDateTime saleStartDateFrom,
            final LocalDateTime saleStartDateTo,
            final LocalDateTime saleEndDateFrom,
            final LocalDateTime saleEndDateTo,
            final ShowCursor cursor
    ) {
        this.category = category;
        this.title = title;
        this.region = region;
        this.saleStartDateFrom = saleStartDateFrom;
        this.saleStartDateTo = saleStartDateTo;
        this.saleEndDateFrom = saleEndDateFrom;
        this.saleEndDateTo = saleEndDateTo;
        this.cursor = cursor;
    }

    public static SaleOpeningSoonSearchParam of(
            final String category,
            final String title,
            final String region,
            final LocalDateTime saleStartDateFrom,
            final LocalDateTime saleStartDateTo,
            final LocalDateTime saleEndDateFrom,
            final LocalDateTime saleEndDateTo,
            final ShowCursor cursor
    ) {
        return new SaleOpeningSoonSearchParam(
                category,
                title,
                ShowParam.parseRegion(region),
                saleStartDateFrom,
                saleStartDateTo,
                saleEndDateFrom,
                saleEndDateTo,
                cursor
        );
    }
}
