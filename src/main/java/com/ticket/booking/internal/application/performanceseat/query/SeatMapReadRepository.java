package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateView;

import java.util.List;

public interface SeatMapReadRepository {

    List<SeatStateView> findSeatStatuses(Long performanceId);
}
