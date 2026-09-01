package com.ticket.core.app.performanceseat.query;

import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.app.performanceseat.query.model.SeatInfoView;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowSeatsUseCase {

    private final ShowRepository showRepository;
    private final SeatMapReadRepository seatMapReadRepository;

    public record Input(Long showId) {
        public Input {
            RequiredInput.positiveId(showId, "showId");
        }
    }

    public record Output(List<SeatInfoView> seats) {
    }

    public Output execute(final Input input) {
        final Show show = showRepository.findById(input.showId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + input.showId()));
        return new Output(seatMapReadRepository.findShowSeats(show.getId()));
    }
}
