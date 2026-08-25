package com.ticket.core.app.performanceseat.query;

import com.ticket.core.app.performanceseat.query.model.SeatInfoView;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.query.ShowFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowSeatsUseCase {

    private final ShowFinder showFinder;
    private final SeatMapQueryRepository seatMapQueryRepository;

    public record Input(Long showId) {
    }

    public record Output(List<SeatInfoView> seats) {
    }

    public Output execute(final Input input) {
        final Show show = showFinder.findById(input.showId());
        return new Output(seatMapQueryRepository.findShowSeats(show.getId()));
    }
}
