package com.ticket.core.app.show.query.model;

import com.ticket.core.domain.show.meta.Region;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;


/**
 * 판매 오픈 예정 공연 목록 조회 요청 파라미터
 */
@Getter
public class SaleOpeningSoonSearchParam {

        private String category;

        private String title;

        private Region region;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime saleStartDateFrom;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime saleStartDateTo;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime saleEndDateFrom;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime saleEndDateTo;

        private String cursor;

    public SaleOpeningSoonSearchParam(
            String category,
            String title,
            Region region,
            LocalDateTime saleStartDateFrom,
            LocalDateTime saleStartDateTo,
            LocalDateTime saleEndDateFrom,
            LocalDateTime saleEndDateTo,
            String cursor
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

}
