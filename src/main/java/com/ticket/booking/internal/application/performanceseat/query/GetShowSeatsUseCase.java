package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.SeatInfoView;
import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSeatMapEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import com.ticket.shared.RequiredInput;

@Service
@RequiredArgsConstructor
public class GetShowSeatsUseCase {

    private final ShowLookup showLookup;

    public record Input(Long showId) {
        public Input {
            RequiredInput.positiveId(showId, "showId");
        }
    }

    public record Output(List<SeatInfoView> seats) {
    }

    public Output execute(final Input input) {
        final List<SeatInfoView> seats = showLookup.getSeatMap(input.showId()).stream()
                .map(this::toSeatInfoView)
                .toList();
        return new Output(seats);
    }

    private SeatInfoView toSeatInfoView(final ShowSeatMapEntry entry) {
        return new SeatInfoView(
                entry.seatId(),
                entry.floor(),
                entry.section(),
                entry.rowNo(),
                entry.seatNo(),
                entry.x(),
                entry.y(),
                entry.gradeCode(),
                entry.gradeName(),
                entry.price()
        );
    }
}
