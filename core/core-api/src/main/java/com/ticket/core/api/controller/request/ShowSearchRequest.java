package com.ticket.core.api.controller.request;

import com.ticket.core.app.show.query.model.ShowSearchCriteria;
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

    public ShowSearchCriteria toCriteria() {
        return ShowSearchCriteria.of(
                keyword,
                category,
                bookingStatus,
                startDateFrom,
                startDateTo,
                region,
                cursor
        );
    }
}
