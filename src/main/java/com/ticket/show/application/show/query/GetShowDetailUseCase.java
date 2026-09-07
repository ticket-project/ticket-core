package com.ticket.show.application.show.query;

import com.ticket.venue.Region;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.BookingStatus;
import com.ticket.show.application.show.query.model.ShowDetailView;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import com.ticket.favorite.ShowLikeQuery;
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
    private final ShowLikeQuery showLikeQuery;

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
            BookingStatus bookingStatus,
            SaleType saleType,
            LocalDateTime saleStartDate,
            LocalDateTime saleEndDate,
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
        final long likeCount = showLikeQuery.countByShowId(input.showId());

        return new Output(
                view.id(), view.title(), view.subTitle(), view.info(), view.startDate(), view.endDate(),
                view.runningMinutes(), view.viewCount(), likeCount, view.bookingStatus(), view.saleType(),
                view.saleStartDate(), view.saleEndDate(), view.image(), view.venue(), view.performer(),
                view.genreNames(), view.priceSummary(), view.performanceDates()
        );
    }

}
