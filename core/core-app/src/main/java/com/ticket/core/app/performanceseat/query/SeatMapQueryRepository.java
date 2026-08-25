package com.ticket.core.app.performanceseat.query;

import com.ticket.core.app.performanceseat.query.model.SeatInfoView;
import com.ticket.core.app.performanceseat.query.model.SeatStateView;

import java.util.List;

public interface SeatMapQueryRepository {

    List<SeatInfoView> findShowSeats(Long showId);

    List<SeatStateView> findSeatStatuses(Long performanceId);
}
