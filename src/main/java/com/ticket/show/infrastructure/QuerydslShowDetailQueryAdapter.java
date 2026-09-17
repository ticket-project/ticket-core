package com.ticket.show.infrastructure;

import static com.ticket.show.domain.QGenre.genre;
import static com.ticket.show.domain.QGrade.grade;
import static com.ticket.show.domain.QPerformer.performer;
import static com.ticket.show.domain.performance.QPerformance.performance;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.domain.show.QShowGenre.showGenre;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.application.port.ShowDetailQueryPort;
import com.ticket.show.application.query.PerformanceDateInfo;
import com.ticket.show.application.query.PerformanceInfo;
import com.ticket.show.application.query.PerformerInfo;
import com.ticket.show.application.query.PriceSummary;
import com.ticket.show.application.query.ShowDetailView;
import com.ticket.show.application.query.ShowGradeView;
import com.ticket.show.domain.Performer;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowCardImagePathConverter;

import lombok.RequiredArgsConstructor;

/**
 * show 자기 DB에서 show 상세 데이터를 읽는 persistence adapter다. venue 표시값 조합은 여기서 하지 않는다 — {@code venueId}
 * scalar만 {@link ShowDetailView}에 담아 넘기고, 실제 venue 조회는 {@code GetShowDetailUseCase}(application)가
 * 한다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslShowDetailQueryAdapter implements ShowDetailQueryPort {
    private final JPAQueryFactory queryFactory;
    private final ShowCardImagePathConverter showCardImagePathConverter;
    private final Clock clock;

    @Override
    public Optional<ShowDetailView> findShowDetail(final Long showId) {
        final Show showEntity = fetchShow(showId);
        if (showEntity == null) {
            return Optional.empty();
        }

        final List<String> genreNames = fetchGenreNames(showId);
        final List<ShowGradeView> grades = fetchGrades(showId);
        final PriceSummary priceSummary = fetchPriceSummary(showId);
        final List<PerformanceDateInfo> performanceDates = fetchPerformanceDates(showId);
        final Performer performerEntity = fetchPerformer(showEntity.getPerformerId());

        return Optional.of(
                toShowDetail(
                        showEntity,
                        performerEntity,
                        genreNames,
                        grades,
                        priceSummary,
                        performanceDates));
    }

    private @Nullable Show fetchShow(final Long showId) {
        return queryFactory.selectFrom(show).where(show.id.eq(showId)).fetchOne();
    }

    /**
     * Performer는 Show와 다른 aggregate라 {@code performerId} scalar로만 연결된다 — 옛 {@code fetchJoin()} 대신
     * 식별자로 따로 조회한다. 같은 module 안의 다른 aggregate라 venue와 달리 여기서 직접 조회해도 된다.
     */
    private @Nullable Performer fetchPerformer(final @Nullable Long performerId) {
        if (performerId == null) {
            return null;
        }
        return queryFactory.selectFrom(performer).where(performer.id.eq(performerId)).fetchOne();
    }

    private List<String> fetchGenreNames(final Long showId) {
        return queryFactory
                .select(genre.name)
                .from(showGenre)
                .join(genre)
                .on(genre.id.eq(showGenre.genreId))
                .where(showGenre.showId.eq(showId))
                .fetch();
    }

    /**
     * ADR 0005: show-level 가격표는 없다. 이 show의 모든 Performance에 배정된 PerformanceGrade.price 중 최소/최대만
     * 파생한다 — 대표 회차 하나의 가격을 show 전체 가격처럼 보여주지 않는다.
     */
    private @Nullable PriceSummary fetchPriceSummary(final Long showId) {
        final com.querydsl.core.Tuple result =
                queryFactory
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
        return new PriceSummary(minPrice, maxPrice);
    }

    /** 기존 프론트 계약에는 공연 가격표가 필요하므로 가장 이른 회차의 등급과 가격을 대표값으로 제공한다. */
    private List<ShowGradeView> fetchGrades(final Long showId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                ShowGradeView.class,
                                performanceGrade.gradeId,
                                grade.name,
                                performanceGrade.price))
                .from(performanceGrade)
                .join(grade)
                .on(grade.id.eq(performanceGrade.gradeId))
                .where(
                        performanceGrade.performance.id.eq(
                                JPAExpressions.select(performance.id.min())
                                        .from(performance)
                                        .where(performance.showId.eq(showId))))
                .orderBy(performanceGrade.sortOrder.asc())
                .fetch();
    }

    private List<PerformanceDateInfo> fetchPerformanceDates(final Long showId) {
        final List<PerformanceInfo> performances =
                fetchPerformances(showId).stream().map(this::toPerformanceInfo).toList();

        return performances.stream()
                .collect(
                        Collectors.groupingBy(
                                performanceInfo -> performanceInfo.startTime().toLocalDate(),
                                LinkedHashMap::new,
                                Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> new PerformanceDateInfo(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<Performance> fetchPerformances(final Long showId) {
        return queryFactory
                .selectFrom(performance)
                .where(performance.showId.eq(showId))
                .orderBy(performance.startTime.asc(), performance.performanceNo.asc())
                .fetch();
    }

    private PerformanceInfo toPerformanceInfo(final Performance performanceEntity) {
        return new PerformanceInfo(
                performanceEntity.getId(),
                performanceEntity.getPerformanceNo(),
                performanceEntity.getStartTime(),
                performanceEntity.getEndTime());
    }

    private ShowDetailView toShowDetail(
            final Show showEntity,
            final @Nullable Performer performerEntity,
            final List<String> genreNames,
            final List<ShowGradeView> grades,
            final @Nullable PriceSummary priceSummary,
            final List<PerformanceDateInfo> performanceDates) {
        final SaleDisplayStatus saleDisplayStatus =
                showEntity.saleDisplayStatusAt(LocalDateTime.now(clock));

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
                showEntity.getVenueId(),
                toPerformerInfo(performerEntity),
                genreNames,
                grades,
                priceSummary,
                performanceDates);
    }

    private @Nullable PerformerInfo toPerformerInfo(final @Nullable Performer performerEntity) {
        if (performerEntity == null) {
            return null;
        }
        return new PerformerInfo(
                performerEntity.getId(),
                performerEntity.getName(),
                performerEntity.getProfileImageUrl());
    }
}
