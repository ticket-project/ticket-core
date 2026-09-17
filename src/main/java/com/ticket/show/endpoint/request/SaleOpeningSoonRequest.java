package com.ticket.show.endpoint.request;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

import com.ticket.show.application.query.SaleOpeningSoonSearchParam;
import com.ticket.show.endpoint.cursor.ShowCursorCodec;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 판매 오픈 예정 공연 목록 조회 요청이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaleOpeningSoonRequest {
    private @Nullable String category;
    private @Nullable String title;

    @Schema(
            allowableValues = {
                "SEOUL",
                "GYEONGGI",
                "INCHEON",
                "GANGWON",
                "CHUNGCHEONG",
                "JEOLLA",
                "GYEONGSANG",
                "JEJU"
            })
    private @Nullable String region;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private @Nullable LocalDateTime saleStartDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private @Nullable LocalDateTime saleStartDateTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private @Nullable LocalDateTime saleEndDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private @Nullable LocalDateTime saleEndDateTo;

    private @Nullable String cursor;

    public SaleOpeningSoonSearchParam toParam(final ShowCursorCodec cursorCodec) {
        return SaleOpeningSoonSearchParam.of(
                category,
                title,
                region,
                saleStartDateFrom,
                saleStartDateTo,
                saleEndDateFrom,
                saleEndDateTo,
                cursorCodec.decode(cursor));
    }
}
