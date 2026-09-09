package com.ticket.show.application;

import com.ticket.venue.Region;
import com.ticket.show.domain.SaleType;
import com.ticket.show.domain.SaleDisplayStatus;
import com.ticket.show.application.ShowDetailView;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import com.ticket.like.LikeQuery;
import com.ticket.like.LikeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowDetailUseCase {

    private final ShowDetailReadRepository showDetailReadRepository;
    private final LikeQuery likeQuery;

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

    public record PerformerInfo(Long id, String name, String profileImageUrl) {
    }

    /**
     * ADR 0005: show-level 가격표(과거 ShowGrade)는 폐기됐다. 등급·가격은 회차(Performance)마다
     * 다를 수 있어 show 상세는 그 회차들의 PerformanceGrade.price 중 최소/최대만 요약해 보여준다.
     * 정확한 가격은 회차를 고른 뒤 그 회차의 등급 API로 확인한다. 이 show에 등급이 하나도 없으면
     * {@code null}이다.
     */
    public record PriceSummary(BigDecimal minPrice, BigDecimal maxPrice) {
    }

    public record PerformanceInfo(
            Long id,
            Long performanceNo,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
    }

    public record PerformanceDateInfo(LocalDate date, List<PerformanceInfo> performances) {
    }

    public record VenueInfo(
            Long id,
            String name,
            String address,
            Region region,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String imageUrl
    ) {
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
        final ShowDetailView view = showDetailReadRepository.findShowDetail(input.showId())
                .orElseThrow(() -> new NotFoundException(
                        "공연을 찾을 수 없습니다. id=" + input.showId()));
        final long likeCount = likeQuery.countByTarget(LikeType.SHOW, input.showId());

        return new Output(
                view.id(), view.title(), view.subTitle(), view.info(), view.startDate(), view.endDate(),
                view.runningMinutes(), view.viewCount(), likeCount, view.saleDisplayStatus(), view.displaySaleType(),
                view.displaySaleStartsAt(), view.displaySaleEndsAt(), view.image(), view.venue(), view.performer(),
                view.genreNames(), view.priceSummary(), view.performanceDates()
        );
    }

}
