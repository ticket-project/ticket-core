package com.ticket.core.app.show.query.model;

import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.support.error.CoreException;
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
        validateRange(saleStartDateFrom, saleStartDateTo, "saleStartDate");
        validateRange(saleEndDateFrom, saleEndDateTo, "saleEndDate");
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
            throw new CoreException(
                    ApplicationErrorType.INVALID_INPUT,
                    field + "From은 " + field + "To보다 늦을 수 없습니다."
            );
        }
    }
}
