package com.ticket.booking.internal.application.publicapi;

import com.ticket.booking.BookingCatalog;
import com.ticket.booking.internal.domain.hold.model.HoldState;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * {@link BookingCatalog}의 booking 소유 구현이다. metadata module은 이 계약을 통해서만
 * booking의 code/label 값을 조합하고, internal enum을 직접 import하지 않는다.
 */
@Service
public class BookingCatalogService implements BookingCatalog {

    @Override
    public List<CodeLabel> performanceSeatStates() {
        return Arrays.stream(PerformanceSeatState.values())
                .map(value -> new CodeLabel(value.getCode(), value.getDescription()))
                .toList();
    }

    @Override
    public List<CodeLabel> holdStates() {
        return Arrays.stream(HoldState.values())
                .map(value -> new CodeLabel(value.getCode(), value.getDescription()))
                .toList();
    }

    @Override
    public List<CodeLabel> orderStates() {
        return Arrays.stream(OrderState.values())
                .map(value -> new CodeLabel(value.getCode(), value.getDescription()))
                .toList();
    }
}
