package com.ticket.show.infrastructure;

import com.ticket.show.application.ShowDetailView;

import com.ticket.show.application.GetShowDetailUseCase;
import com.ticket.show.application.ShowDetailReadRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.domain.Performance;
import com.ticket.show.domain.Show;
import com.ticket.show.domain.ShowCardImagePathConverter;
import com.ticket.show.domain.Performer;
import com.ticket.show.domain.SaleDisplayStatus;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ticket.show.domain.QPerformance.performance;
import static com.ticket.show.domain.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.QGenre.genre;
import static com.ticket.show.domain.QShowGenre.showGenre;
import static com.ticket.show.domain.QPerformer.performer;
import static com.ticket.show.domain.QShow.show;

@Repository
@RequiredArgsConstructor
public class QuerydslShowDetailReadRepository implements ShowDetailReadRepository {

    private final JPAQueryFactory queryFactory;
    private final ShowCardImagePathConverter showCardImagePathConverter;
    private final VenueLookup venueLookup;
    private final Clock clock;

    @Override
    public Optional<ShowDetailView> findShowDetail(final Long showId) {
        final Show showEntity = fetchShow(showId);
        if (showEntity == null) {
            return Optional.empty();
        }

        final List<String> genreNames = fetchGenreNames(showId);
        final GetShowDetailUseCase.PriceSummary priceSummary = fetchPriceSummary(showId);
        final List<GetShowDetailUseCase.PerformanceDateInfo> performanceDates = fetchPerformanceDates(showId);
        final VenueSummary venue = showEntity.getVenueId() == null
                ? null
                : venueLookup.findSummary(showEntity.getVenueId()).orElse(null);
        final Performer performerEntity = fetchPerformer(showEntity.getPerformerId());

        return Optional.of(toShowDetail(showEntity, venue, performerEntity, genreNames, priceSummary, performanceDates));
    }

    private Show fetchShow(final Long showId) {
        return queryFactory
                .selectFrom(show)
                .where(show.id.eq(showId))
                .fetchOne();
    }

    /**
     * Performer는 Show와 다른 aggregate라 {@code performerId} scalar로만 연결된다 — 옛
     * {@code fetchJoin()} 대신 식별자로 따로 조회한다. venue 표시값을 {@code VenueLookup}으로 따로
     * 채우는 것과 같은 형태다.
     */
    private Performer fetchPerformer(final Long performerId) {
        if (performerId == null) {
            return null;
        }
        return queryFactory
                .selectFrom(performer)
                .where(performer.id.eq(performerId))
                .fetchOne();
    }

    private List<String> fetchGenreNames(final Long showId) {
        return queryFactory
                .select(genre.name)
                .from(showGenre)
                .join(genre).on(genre.id.eq(showGenre.genreId))
                .where(showGenre.showId.eq(showId))
                .fetch();
    }

    /**
     * ADR 0005: show-level 가격표는 없다. 이 show의 모든 Performance에 배정된 PerformanceGrade.price
     * 중 최소/최대만 파생한다 — 대표 회차 하나의 가격을 show 전체 가격처럼 보여주지 않는다.
     */
    private GetShowDetailUseCase.PriceSummary fetchPriceSummary(final Long showId) {
        final com.querydsl.core.Tuple result = queryFactory
                .select(performanceGrade.price.min(), performanceGrade.price.max())
                .from(performanceGrade)
                .join(performanceGrade.performance, performance)
                .where(performance.showId.eq(showId))
                .fetchOne();
        if (result == null) {
            return null;
        }
        final var minPrice = result.get(performanceGrade.price.min());
        final var maxPrice = result.get(performanceGrade.price.max());
        if (minPrice == null || maxPrice == null) {
            return null;
        }
        return new GetShowDetailUseCase.PriceSummary(minPrice, maxPrice);
    }

    private List<GetShowDetailUseCase.PerformanceDateInfo> fetchPerformanceDates(final Long showId) {
        final List<GetShowDetailUseCase.PerformanceInfo> performances = fetchPerformances(showId).stream()
                .map(this::toPerformanceInfo)
                .toList();

        return performances.stream()
                .collect(Collectors.groupingBy(
                        performanceInfo -> performanceInfo.startTime().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> new GetShowDetailUseCase.PerformanceDateInfo(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<Performance> fetchPerformances(final Long showId) {
        return queryFactory
                .selectFrom(performance)
                .where(performance.showId.eq(showId))
                .orderBy(performance.startTime.asc(), performance.performanceNo.asc())
                .fetch();
    }

    private GetShowDetailUseCase.PerformanceInfo toPerformanceInfo(final Performance performanceEntity) {
        return new GetShowDetailUseCase.PerformanceInfo(
                performanceEntity.getId(),
                performanceEntity.getPerformanceNo(),
                performanceEntity.getStartTime(),
                performanceEntity.getEndTime()
        );
    }

    private ShowDetailView toShowDetail(
            final Show showEntity,
            final VenueSummary venue,
            final Performer performerEntity,
            final List<String> genreNames,
            final GetShowDetailUseCase.PriceSummary priceSummary,
            final List<GetShowDetailUseCase.PerformanceDateInfo> performanceDates
    ) {
        final SaleDisplayStatus saleDisplayStatus = showEntity.saleDisplayStatusAt(LocalDateTime.now(clock));

        return new ShowDetailView(
                showEntity.getId(),
                showEntity.getTitle(),
                showEntity.getSubTitle(),
                showEntity.getInfo(),
                showEntity.getStartDate(),
                showEntity.getEndDate(),
                showEntity.getRunningMinutes(),
                showEntity.getViewCount(),
                saleDisplayStatus,
                showEntity.getDisplaySaleType(),
                showEntity.getDisplaySaleStartsAt(),
                showEntity.getDisplaySaleEndsAt(),
                showCardImagePathConverter.toCardImage(showEntity.getImage()),
                toVenueInfo(venue),
                toPerformerInfo(performerEntity),
                genreNames,
                priceSummary,
                performanceDates
        );
    }

    private GetShowDetailUseCase.PerformerInfo toPerformerInfo(final Performer performerEntity) {
        if (performerEntity == null) {
            return null;
        }
        return new GetShowDetailUseCase.PerformerInfo(
                performerEntity.getId(),
                performerEntity.getName(),
                performerEntity.getProfileImageUrl()
        );
    }

    private GetShowDetailUseCase.VenueInfo toVenueInfo(final VenueSummary venue) {
        if (venue == null) {
            return null;
        }
        return new GetShowDetailUseCase.VenueInfo(
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
