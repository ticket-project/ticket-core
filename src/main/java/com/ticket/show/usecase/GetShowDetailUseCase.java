package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.like.api.LikeQueryApi;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.GradeRepository;
import com.ticket.show.domain.Performer;
import com.ticket.show.domain.PerformerRepository;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.show.exception.ShowNotFoundException;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * show 상세 응답은 show 자기 DB의 조각들({@link ShowQuerydslRepository})에 venue 표시값과 찜 개수를 조합한 결과다. 그 조합은 이 use case가 한다 — local
 * 조회는 venue도 like도 모르고 {@code venueId} scalar만 넘긴다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowDetailUseCase {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final ShowRepository showRepository;
    private final GradeRepository gradeRepository;
    private final PerformerRepository performerRepository;
    private final PerformanceRepository performanceRepository;
    private final LikeQueryApi likeQueryApi;
    private final VenueLookupApi venueLookup;
    private final ShowCardImagePathConverter showCardImagePathConverter;
    private final Clock clock;

    public record Input(Long showId) {
        public Input {
            showId = requirePositiveId(showId, "showId");
        }
    }

    /**
     * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름
     * {@code bookingStatus}/{@code saleType}/{@code saleStartDate}/{@code saleEndDate}는 {@code ticket-fe}가 이미 쓰고 있어
     * {@link JsonProperty}로 그대로 고정한다.
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
            VenueInfo venue,
            @Nullable PerformerInfo performer,
            List<String> genreNames,
            List<GradeInfo> grades,
            @Nullable PriceSummary priceSummary,
            List<PerformanceDateInfo> performanceDates) {}

    /** show 상세에 쓰는 venue 표시값 조합 결과다. venue module의 {@code VenueSnapshot}를 이 응답 모양(좌석 배치 등 여기서 쓰지 않는 필드는 뺀)으로 옮겨 담는다. */
    public record VenueInfo(
            Long id,
            @Nullable String name,
            @Nullable String address,
            @Nullable String region,
            @Nullable BigDecimal latitude,
            @Nullable BigDecimal longitude,
            @Nullable String phone,
            @Nullable String imageUrl) {}

    public record PerformerInfo(Long id, String name, String profileImageUrl) {}

    /** 기존 공연 상세 응답에서 사용하는 대표 회차의 등급별 가격이다. {@code id}는 등급 id다. */
    public record GradeInfo(Long id, String gradeName, BigDecimal price) {}

    public record PerformanceDateInfo(LocalDate date, List<PerformanceInfo> performances) {}

    public record PerformanceInfo(Long id, Long performanceNo, LocalDateTime startTime, LocalDateTime endTime) {}

    /**
     * ADR 0005: show-level 가격표(과거 ShowGrade)는 폐기됐다. 등급·가격은 회차(Performance)마다 다를 수 있어 show 상세는 그 회차들의
     * PerformanceGrade.price 중 최소/최대만 요약해 보여준다. 정확한 가격은 회차를 고른 뒤 그 회차의 등급 API로 확인한다. 이 show에 등급이 하나도 없으면
     * {@code null}이다.
     *
     * <p>DB가 계산한 집계 결과다 — 가격 전체를 메모리로 읽어 세지 않는다. 그래서 {@code ShowQuerydslRepository}가 이 타입으로 돌려준다.
     */
    public record PriceSummary(BigDecimal minPrice, BigDecimal maxPrice) {}

    public Output execute(final Input input) {
        final Long showId = input.showId();
        final Show show = showRepository.findById(showId).orElseThrow(() -> new ShowNotFoundException(showId));

        return new Output(
                show.getId(),
                show.getTitle(),
                show.getSubTitle(),
                show.getInfo(),
                show.getStartDate(),
                show.getEndDate(),
                show.getRunningMinutes(),
                show.getViewCount(),
                likeQueryApi.countByTarget("show", showId),
                show.saleDisplayStatusAt(LocalDateTime.now(clock)),
                show.getDisplaySaleType(),
                show.getDisplaySaleStartsAt(),
                show.getDisplaySaleEndsAt(),
                showCardImagePathConverter.toCardImage(show.getImage()),
                resolveVenue(show.getVenueId()),
                resolvePerformer(show.getPerformerId()),
                showRepository.findGenreNames(showId),
                resolveGrades(showId),
                showQuerydslRepository.findPriceSummary(showId),
                resolvePerformanceDates(showId));
    }

    /**
     * 대표 회차의 등급 배정과 그 등급 이름을 조합한다. 표시 순서는 {@code PerformanceGrade.sortOrder}이고, 이름을 찾지 못한 등급은 제외한다 — 예전 {@code join
     * grade}가 그랬듯 조용히 빠진다.
     */
    private List<GradeInfo> resolveGrades(final Long showId) {
        final List<PerformanceGrade> performanceGrades =
                performanceRepository.findRepresentativePerformanceGrades(showId);
        final Map<Long, Grade> gradesById = gradeRepository.findGradeNames(
                performanceGrades.stream().map(PerformanceGrade::getGradeId).collect(Collectors.toSet()));

        return performanceGrades.stream()
                .map(performanceGrade -> toGradeInfo(performanceGrade, gradesById))
                .filter(Objects::nonNull)
                .toList();
    }

    private @Nullable GradeInfo toGradeInfo(
            final PerformanceGrade performanceGrade, final Map<Long, Grade> gradesById) {
        final Grade grade = gradesById.get(performanceGrade.getGradeId());
        if (grade == null) {
            return null;
        }
        return new GradeInfo(performanceGrade.getGradeId(), grade.getName(), performanceGrade.getPrice());
    }

    /** 회차를 날짜별로 묶는다. 조회가 이미 시작 시각·회차 번호 순으로 주므로 그 순서를 그대로 유지한다. */
    private List<PerformanceDateInfo> resolvePerformanceDates(final Long showId) {
        return performanceRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(showId).stream()
                .collect(Collectors.groupingBy(
                        performance -> performance.getStartTime().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toPerformanceInfo, Collectors.toList())))
                .entrySet()
                .stream()
                .map(entry -> new PerformanceDateInfo(entry.getKey(), entry.getValue()))
                .toList();
    }

    private PerformanceInfo toPerformanceInfo(final Performance performance) {
        return new PerformanceInfo(
                performance.getId(),
                performance.getPerformanceNo(),
                performance.getStartTime(),
                performance.getEndTime());
    }

    private @Nullable PerformerInfo resolvePerformer(final @Nullable Long performerId) {
        if (performerId == null) {
            return null;
        }
        return performerRepository
                .findById(performerId)
                .map(this::toPerformerInfo)
                .orElse(null);
    }

    private PerformerInfo toPerformerInfo(final Performer performer) {
        return new PerformerInfo(performer.getId(), performer.getName(), performer.getProfileImageUrl());
    }

    private VenueInfo resolveVenue(final long venueId) {
        return toVenueInfo(venueLookup.getVenueSnapshot(venueId));
    }

    private VenueInfo toVenueInfo(final VenueSnapshot venue) {
        return new VenueInfo(
                venue.venueId(),
                venue.name(),
                venue.address(),
                venue.region() == null ? null : venue.region().code(),
                venue.latitude(),
                venue.longitude(),
                venue.phone(),
                venue.imageUrl());
    }
}
