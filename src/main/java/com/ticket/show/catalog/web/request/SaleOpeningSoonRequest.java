package com.ticket.show.catalog.web.request;

import com.ticket.show.catalog.web.support.cursor.ShowCursorCodec;
import com.ticket.show.catalog.application.SaleOpeningSoonSearchParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 판매 오픈 예정 공연 목록 조회 요청이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaleOpeningSoonRequest {

    private String category;

    private String title;

    @Schema(allowableValues = {"SEOUL", "GYEONGGI", "INCHEON", "GANGWON", "CHUNGCHEONG",
            "JEOLLA", "GYEONGSANG", "JEJU"})
    private String region;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime saleStartDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime saleStartDateTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime saleEndDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime saleEndDateTo;

    private String cursor;

    public SaleOpeningSoonSearchParam toParam(final ShowCursorCodec cursorCodec) {
        return SaleOpeningSoonSearchParam.of(
                category,
                title,
                region,
                saleStartDateFrom,
                saleStartDateTo,
                saleEndDateFrom,
                saleEndDateTo,
                cursorCodec.decode(cursor)
        );
    }
}
