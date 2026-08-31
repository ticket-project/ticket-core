package com.ticket.core.infra.show.query;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.ticket.core.domain.show.model.BookingStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.ticket.core.domain.show.model.QShow.show;

@Component
public class BookingStatusPredicateFactory {

    public BooleanExpression condition(final BookingStatus bookingStatus, final LocalDateTime now) {
        if (bookingStatus == null) {
            return null;
        }

        return switch (bookingStatus) {
            case BEFORE_OPEN -> show.saleStartDate.gt(now);
            case ON_SALE -> show.saleStartDate.loe(now).and(show.saleEndDate.goe(now));
            case CLOSED -> show.saleEndDate.lt(now);
        };
    }
}
