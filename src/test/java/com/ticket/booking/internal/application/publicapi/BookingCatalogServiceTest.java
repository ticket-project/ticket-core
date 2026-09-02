package com.ticket.booking.internal.application.publicapi;

import com.ticket.booking.BookingCatalog;
import com.ticket.booking.internal.domain.hold.model.HoldState;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class BookingCatalogServiceTest {

    private final BookingCatalogService service = new BookingCatalogService();

    @Test
    void PerformanceSeatState_모든_값을_code_label로_반환한다() {
        assertThat(service.performanceSeatStates())
                .hasSize(PerformanceSeatState.values().length)
                .contains(new BookingCatalog.CodeLabel("AVAILABLE", "예매가능"))
                .contains(new BookingCatalog.CodeLabel("RESERVED", "예매완료"));
    }

    @Test
    void HoldState_모든_값을_code_label로_반환한다() {
        assertThat(service.holdStates()).hasSize(HoldState.values().length);
    }

    @Test
    void OrderState_모든_값을_code_label로_반환한다() {
        assertThat(service.orderStates()).hasSize(OrderState.values().length);
    }
}
