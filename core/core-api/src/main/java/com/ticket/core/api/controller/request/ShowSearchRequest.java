package com.ticket.core.api.controller.request;

import com.ticket.core.domain.show.BookingStatus;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
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
    private BookingStatus bookingStatus;
    private LocalDate startDateFrom;
    private LocalDate startDateTo;
    private Region region;
    private String cursor;

    public ShowSearchCriteria toCriteria() {
        return new ShowSearchCriteria(
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
