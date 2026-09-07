package com.ticket.show.infrastructure.show.query;

import com.ticket.show.application.show.query.model.ShowDetailView;

import com.ticket.show.application.show.query.GetShowDetailUseCase;
import com.ticket.show.application.show.query.ShowDetailReadRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.policy.BookingEntryResolver;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.image.ShowCardImagePathConverter;
import com.ticket.show.domain.show.Performer;
import com.ticket.show.domain.show.BookingStatus;
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

import static com.ticket.show.domain.performance.QPerformance.performance;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.show.QGenre.genre;
import static com.ticket.show.domain.show.QShowGenre.showGenre;
import static com.ticket.show.domain.show.QPerformer.performer;
import static com.ticket.show.domain.show.QShow.show;

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

        return Optional.of(toShowDetail(showEntity, venue, genreNames, priceSummary, performanceDates));
    }

    private Show fetchShow(final Long showId) {
        return queryFactory
                .selectFrom(show)
                .leftJoin(show.performer, performer).fetchJoin()
                .where(show.id.eq(showId))
                .fetchOne();
    }

    private List<String> fetchGenreNames(final Long showId) {
        return queryFactory
                .select(genre.name)
                .from(showGenre)
                .join(showGenre.genre, genre)
                .where(showGenre.show.id.eq(showId))
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
                .where(performance.show.id.eq(showId))
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
                .leftJoin(performance.queuePolicy).fetchJoin()
                .where(performance.show.id.eq(showId))
                .orderBy(performance.startTime.asc(), performance.performanceNo.asc())
                .fetch();
    }

    private GetShowDetailUseCase.PerformanceInfo toPerformanceInfo(final Performance performanceEntity) {
        final BookingEntryResolver.Output bookingEntry =
                BookingEntryResolver.resolve(performanceEntity.getId(), performanceEntity, LocalDateTime.now(clock));

        return new GetShowDetailUseCase.PerformanceInfo(
                performanceEntity.getId(),
                performanceEntity.getPerformanceNo(),
                performanceEntity.getStartTime(),
                performanceEntity.getEndTime(),
                performanceEntity.getOrderOpenTime(),
                performanceEntity.getOrderCloseTime(),
                bookingEntry.entryType(),
                bookingEntry.queueRequired(),
                bookingEntry.redirectUrl(),
                bookingEntry.queueEnterUrl()
        );
    }

    private ShowDetailView toShowDetail(
            final Show showEntity,
            final VenueSummary venue,
            final List<String> genreNames,
            final GetShowDetailUseCase.PriceSummary priceSummary,
            final List<GetShowDetailUseCase.PerformanceDateInfo> performanceDates
    ) {
        final BookingStatus bookingStatus = showEntity.getBookingStatus(LocalDateTime.now(clock));

        return new ShowDetailView(
                showEntity.getId(),
                showEntity.getTitle(),
                showEntity.getSubTitle(),
                showEntity.getInfo(),
                showEntity.getStartDate(),
                showEntity.getEndDate(),
                showEntity.getRunningMinutes(),
                showEntity.getViewCount(),
                bookingStatus,
                showEntity.getSaleType(),
                showEntity.getSaleStartDate(),
                showEntity.getSaleEndDate(),
                showCardImagePathConverter.toCardImage(showEntity.getImage()),
                toVenueInfo(venue),
                toPerformerInfo(showEntity.getPerformer()),
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
