package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * show에 연결된 공연장의 좌석 맵 배치를 조회한다. venue 표시값은 venue module의 {@link VenueLookupApi}에서 조회한다 — show는 물리
 * 공연장 entity를 참조하지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowVenueLayoutUseCase {
    private final ShowRepository showRepository;
    private final VenueLookupApi venueLookup;

    public record Input(Long showId) {
        public Input {
            showId = requirePositiveId(showId, "showId");
        }
    }

    public record Output(
            @Nullable String name, int viewBoxWidth, int viewBoxHeight, double seatDiameter) {}

    public Output execute(final Input input) {
        final Show show =
                showRepository
                        .findById(input.showId())
                        .orElseThrow(
                                () -> new NotFoundException("공연을 찾을 수 없습니다. id=" + input.showId()));

        final Long venueId = show.getVenueId();
        final VenueSnapshot venue =
                venueId == null ? null : venueLookup.findSummary(venueId).orElse(null);
        if (venue == null) {
            throw new NotFoundException("공연에 연결된 공연장을 찾을 수 없습니다.");
        }

        return new Output(
                venue.name(),
                venue.seatMapLayout().viewBoxWidth(),
                venue.seatMapLayout().viewBoxHeight(),
                venue.seatMapLayout().seatDiameter());
    }
}
