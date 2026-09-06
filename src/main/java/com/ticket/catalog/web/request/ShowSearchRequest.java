package com.ticket.catalog.web.request;

import com.ticket.catalog.web.support.cursor.ShowCursorCodec;
import com.ticket.catalog.application.show.query.model.ShowSearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShowSearchRequest {
    private String keyword;
    private String category;
    @Schema(allowableValues = {"BEFORE_OPEN", "ON_SALE", "CLOSED"})
    private String bookingStatus;
    private LocalDate startDateFrom;
    private LocalDate startDateTo;
    @Schema(allowableValues = {"SEOUL", "GYEONGGI", "INCHEON", "GANGWON", "CHUNGCHEONG",
            "JEOLLA", "GYEONGSANG", "JEJU"})
    private String region;
    private String cursor;

    /**
     * 집계는 커서를 쓰지 않는다. 잘못된 커서 문자열 때문에 건수 조회가 실패하지 않게
     * 커서를 해석하지 않고 조건만 만든다.
     */
    public ShowSearchCriteria toCountCriteria() {
        return ShowSearchCriteria.of(
                keyword,
                category,
                bookingStatus,
                startDateFrom,
                startDateTo,
                region,
                null
        );
    }

    public ShowSearchCriteria toCriteria(final ShowCursorCodec cursorCodec) {
        return ShowSearchCriteria.of(
                keyword,
                category,
                bookingStatus,
                startDateFrom,
                startDateTo,
                region,
                cursorCodec.decode(cursor)
        );
    }
}
