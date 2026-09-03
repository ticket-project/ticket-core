package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.SeatInfoView;
import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSeatMapEntry;
import com.ticket.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetShowSeatsUseCase {

    private final ShowLookup showLookup;

    public record Input(Long showId) {
        public Input {
            if (showId == null) {
                throw new InvalidRequestException("showId는 필수입니다.");
            }
            if (showId <= 0) {
                throw new InvalidRequestException("showId는 양수여야 합니다.");
            }
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
