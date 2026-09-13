package com.ticket.show.performance.application.usecase;

import com.ticket.show.catalog.domain.Show;
import com.ticket.show.catalog.domain.ShowRepository;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * show에 연결된 공연장의 좌석 맵 배치를 조회한다. venue 표시값은 venue module의
 * {@link VenueLookup}에서 조회한다 — show는 물리 공연장 entity를 참조하지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetVenueLayoutUseCase {

    private final ShowRepository showRepository;
    private final VenueLookup venueLookup;

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

    public record Output(String name,
                         int viewBoxWidth,
                         int viewBoxHeight,
                         double seatDiameter) {}

    public Output execute(final Input input) {
        final Show show = showRepository.findById(input.showId())
                .orElseThrow(() -> new NotFoundException("공연을 찾을 수 없습니다. id=" + input.showId()));

        final Long venueId = show.getVenueId();
        final VenueSummary venue = venueId == null ? null : venueLookup.findSummary(venueId).orElse(null);
        if (venue == null) {
            throw new NotFoundException("공연에 연결된 공연장을 찾을 수 없습니다.");
        }

        return new Output(
                venue.name(),
                venue.seatMapLayout().viewBoxWidth(),
                venue.seatMapLayout().viewBoxHeight(),
                venue.seatMapLayout().seatDiameter()
        );
    }
}
