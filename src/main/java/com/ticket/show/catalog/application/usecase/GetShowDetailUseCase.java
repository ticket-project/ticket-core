package com.ticket.show.catalog.application.usecase;

import com.ticket.show.performance.application.PerformanceDateInfo;
import com.ticket.show.catalog.application.PerformerInfo;
import com.ticket.show.catalog.application.PriceSummary;
import com.ticket.show.catalog.application.ShowDetailView;
import com.ticket.show.catalog.application.VenueInfo;

import com.ticket.show.catalog.application.port.ShowDetailQueryPort;

import com.ticket.show.catalog.domain.SaleType;
import com.ticket.show.catalog.domain.SaleDisplayStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import com.ticket.like.LikeQuery;
import com.ticket.like.LikeType;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * show 상세 조회는 자기 DB 조회({@link ShowDetailQueryPort})에 venue 표시값(like 개수도
 * 마찬가지)을 조합한 결과다. 그 조합은 이 use case가 한다 — persistence adapter
 * ({@code QuerydslShowDetailQueryPort})는 venue를 모르고 {@code venueId} scalar만 넘긴다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowDetailUseCase {

    private final ShowDetailQueryPort showDetailQueryPort;
    private final LikeQuery likeQuery;
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

    /**
     * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름
     * {@code bookingStatus}/{@code saleType}/{@code saleStartDate}/{@code saleEndDate}는
     * {@code ticket-fe}가 이미 쓰고 있어 {@link JsonProperty}로 그대로 고정한다.
     */
    public record Output(
            Long id,
            String title,
            String subTitle,
            String info,
            LocalDate startDate,
            LocalDate endDate,
            Integer runningMinutes,
            long viewCount,
            long likeCount,
            @JsonProperty("bookingStatus") SaleDisplayStatus saleDisplayStatus,
            @JsonProperty("saleType") SaleType displaySaleType,
            @JsonProperty("saleStartDate") LocalDateTime displaySaleStartsAt,
            @JsonProperty("saleEndDate") LocalDateTime displaySaleEndsAt,
            String image,
            VenueInfo venue,
            PerformerInfo performer,
            List<String> genreNames,
            PriceSummary priceSummary,
            List<PerformanceDateInfo> performanceDates
    ) {
    }

    public Output execute(final Input input) {
        final ShowDetailView view = showDetailQueryPort.findShowDetail(input.showId())
                .orElseThrow(() -> new NotFoundException(
                        "공연을 찾을 수 없습니다. id=" + input.showId()));
        final long likeCount = likeQuery.countByTarget(LikeType.SHOW, input.showId());
        final VenueInfo venue = resolveVenue(view.venueId());

        return new Output(
                view.id(), view.title(), view.subTitle(), view.info(), view.startDate(), view.endDate(),
                view.runningMinutes(), view.viewCount(), likeCount, view.saleDisplayStatus(), view.displaySaleType(),
                view.displaySaleStartsAt(), view.displaySaleEndsAt(), view.image(), venue, view.performer(),
                view.genreNames(), view.priceSummary(), view.performanceDates()
        );
    }

    private VenueInfo resolveVenue(final Long venueId) {
        if (venueId == null) {
            return null;
        }
        return venueLookup.findSummary(venueId).map(this::toVenueInfo).orElse(null);
    }

    private VenueInfo toVenueInfo(final VenueSummary venue) {
        return new VenueInfo(
                venue.venueId(),
                venue.name(),
                venue.address(),
                venue.region(),
                venue.latitude(),
                venue.longitude(),
                venue.phone(),
                venue.imageUrl()
        );
    }

}
