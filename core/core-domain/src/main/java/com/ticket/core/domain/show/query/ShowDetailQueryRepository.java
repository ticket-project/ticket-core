package com.ticket.core.domain.show.query;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.BookingEntryResolver;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.image.ShowCardImagePathConverter;
import com.ticket.core.domain.show.mapping.ShowGrade;
import com.ticket.core.domain.show.performer.Performer;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.core.domain.show.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ticket.core.domain.performance.model.QPerformance.performance;
import static com.ticket.core.domain.show.model.QGenre.genre;
import static com.ticket.core.domain.show.mapping.QShowGenre.showGenre;
import static com.ticket.core.domain.show.mapping.QShowGrade.showGrade;
import static com.ticket.core.domain.show.performer.QPerformer.performer;
import static com.ticket.core.domain.show.model.QShow.show;
import static com.ticket.core.domain.showlike.model.QShowLike.showLike;

@Repository
@RequiredArgsConstructor
public class ShowDetailQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final ShowCardImagePathConverter showCardImagePathConverter;
    private final Clock clock;

    public Optional<GetShowDetailUseCase.Output> findShowDetail(final Long showId) {
        final Show showEntity = fetchShow(showId);
        if (showEntity == null) {
            return Optional.empty();
        }

        final List<String> genreNames = fetchGenreNames(showId);
        final List<GetShowDetailUseCase.GradeInfo> grades = fetchGrades(showId);
        final List<GetShowDetailUseCase.PerformanceDateInfo> performanceDates = fetchPerformanceDates(showId);
        final long likeCount = fetchLikeCount(showId);

        return Optional.of(toShowDetail(showEntity, genreNames, grades, performanceDates, likeCount));
    }

    private Show fetchShow(final Long showId) {
        return queryFactory
                .selectFrom(show)
                .leftJoin(show.performer, performer).fetchJoin()
                .leftJoin(show.venue).fetchJoin()
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

    private List<GetShowDetailUseCase.GradeInfo> fetchGrades(final Long showId) {
        return queryFactory
                .selectFrom(showGrade)
                .where(showGrade.show.id.eq(showId))
                .orderBy(showGrade.sortOrder.asc())
                .fetch()
                .stream()
                .map(this::toGradeInfo)
                .toList();
    }

    private GetShowDetailUseCase.GradeInfo toGradeInfo(final ShowGrade grade) {
        return new GetShowDetailUseCase.GradeInfo(
                grade.getId(),
                grade.getGradeCode(),
                grade.getGradeName(),
                grade.getPrice(),
                grade.getSortOrder()
        );
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

    private long fetchLikeCount(final Long showId) {
        return Optional.ofNullable(
                queryFactory.select(showLike.count())
                        .from(showLike)
                        .where(showLike.show.id.eq(showId))
                        .fetchOne()
        ).orElse(0L);
    }

    private GetShowDetailUseCase.Output toShowDetail(
            final Show showEntity,
            final List<String> genreNames,
            final List<GetShowDetailUseCase.GradeInfo> grades,
            final List<GetShowDetailUseCase.PerformanceDateInfo> performanceDates,
            final long likeCount
    ) {
        final BookingStatus bookingStatus = showEntity.getBookingStatus(LocalDateTime.now(clock));

        return new GetShowDetailUseCase.Output(
                showEntity.getId(),
                showEntity.getTitle(),
                showEntity.getSubTitle(),
                showEntity.getInfo(),
                showEntity.getStartDate(),
                showEntity.getEndDate(),
                showEntity.getRunningMinutes(),
                showEntity.getViewCount(),
                likeCount,
                bookingStatus,
                showEntity.getSaleType(),
                showEntity.getSaleStartDate(),
                showEntity.getSaleEndDate(),
                showCardImagePathConverter.toCardImage(showEntity.getImage()),
                toVenueInfo(showEntity.getVenue()),
                toPerformerInfo(showEntity.getPerformer()),
                genreNames,
                grades,
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

    private GetShowDetailUseCase.VenueInfo toVenueInfo(final Venue venueEntity) {
        if (venueEntity == null) {
            return null;
        }
        return new GetShowDetailUseCase.VenueInfo(
                venueEntity.getId(),
                venueEntity.getName(),
                venueEntity.getAddress(),
                venueEntity.getRegion(),
                venueEntity.getLatitude(),
                venueEntity.getLongitude(),
                venueEntity.getPhone(),
                venueEntity.getImageUrl()
        );
    }
}
