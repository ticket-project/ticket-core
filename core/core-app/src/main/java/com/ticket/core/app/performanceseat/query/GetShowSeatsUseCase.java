package com.ticket.core.app.performanceseat.query;

import com.ticket.core.app.performanceseat.query.model.SeatInfoView;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowSeatsUseCase {

    private final ShowRepository showRepository;
    private final SeatMapQueryRepository seatMapQueryRepository;

    public record Input(Long showId) {
    }

    public record Output(List<SeatInfoView> seats) {
    }

    public Output execute(final Input input) {
        final Show show = showRepository.getById(input.showId());
        return new Output(seatMapQueryRepository.findShowSeats(show.getId()));
    }
}
