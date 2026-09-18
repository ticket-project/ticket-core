package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeType;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.domain.Performer;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowCardImagePathConverter;
import com.ticket.show.persistence.ShowQueryRepository;
import com.ticket.show.query.PerformanceDateInfo;
import com.ticket.show.query.PerformerInfo;
import com.ticket.show.query.PriceSummary;
import com.ticket.show.query.VenueInfo;
import com.ticket.show.usecase.view.ShowGradeView;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSummary;

import lombok.RequiredArgsConstructor;

/**
 * show 상세 응답은 show 자기 DB의 조각들({@link ShowQueryRepository})에 venue 표시값과 찜 개수를 조합한 결과다. 그 조합은 이 use
 * case가 한다 — local 조회는 venue도 like도 모르고 {@code venueId} scalar만 넘긴다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowDetailUseCase {
    private final ShowQueryRepository showQueryRepository;
    private final LikeQueryApi likeQuery;
    private final VenueLookupApi venueLookup;
    private final ShowCardImagePathConverter showCardImagePathConverter;
    private final Clock clock;

    public record Input(Long showId) {
        public Input {
            showId = requirePositiveId(showId, "showId");
        }
    }

    /**
     * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름 {@code bookingStatus}/{@code
     * saleType}/{@code saleStartDate}/{@code saleEndDate}는 {@code ticket-fe}가 이미 쓰고 있어 {@link
     * JsonProperty}로 그대로 고정한다.
     */
    public record Output(
            Long id,
            @Nullable String title,
            @Nullable String subTitle,
            String info,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable Integer runningMinutes,
            long viewCount,
            long likeCount,
            @JsonProperty("bookingStatus") SaleDisplayStatus saleDisplayStatus,
            @JsonProperty("saleType") SaleType displaySaleType,
            @JsonProperty("saleStartDate") @Nullable LocalDateTime displaySaleStartsAt,
            @JsonProperty("saleEndDate") @Nullable LocalDateTime displaySaleEndsAt,
            @Nullable String image,
            @Nullable VenueInfo venue,
            @Nullable PerformerInfo performer,
            List<String> genreNames,
            List<ShowGradeView> grades,
            @Nullable PriceSummary priceSummary,
            List<PerformanceDateInfo> performanceDates) {}

    public Output execute(final Input input) {
        final Long showId = input.showId();
        final Show show =
                showQueryRepository
                        .findShow(showId)
                        .orElseThrow(() -> new NotFoundException("공연을 찾을 수 없습니다. id=" + showId));

        return new Output(
                show.getId(),
                show.getTitle(),
                show.getSubTitle(),
                show.getInfo(),
                show.getStartDate(),
                show.getEndDate(),
                show.getRunningMinutes(),
                show.getViewCount(),
                likeQuery.countByTarget(LikeType.SHOW, showId),
                show.saleDisplayStatusAt(LocalDateTime.now(clock)),
                show.getDisplaySaleType(),
                show.getDisplaySaleStartsAt(),
                show.getDisplaySaleEndsAt(),
                showCardImagePathConverter.toCardImage(show.getImage()),
                resolveVenue(show.getVenueId()),
                resolvePerformer(show.getPerformerId()),
                showQueryRepository.findGenreNames(showId),
                showQueryRepository.findGrades(showId),
                showQueryRepository.findPriceSummary(showId),
                showQueryRepository.findPerformanceDates(showId));
    }

    private @Nullable PerformerInfo resolvePerformer(final @Nullable Long performerId) {
        if (performerId == null) {
            return null;
        }
        return showQueryRepository
                .findPerformer(performerId)
                .map(this::toPerformerInfo)
                .orElse(null);
    }

    private PerformerInfo toPerformerInfo(final Performer performer) {
        return new PerformerInfo(
                performer.getId(), performer.getName(), performer.getProfileImageUrl());
    }

    private @Nullable VenueInfo resolveVenue(final @Nullable Long venueId) {
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
                venue.imageUrl());
    }
}
