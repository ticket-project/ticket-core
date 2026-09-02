package com.ticket.catalog.internal.application.show.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.catalog.internal.domain.performance.policy.BookingEntryResolver;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.SaleType;
import com.ticket.catalog.internal.domain.show.BookingStatus;
import com.ticket.catalog.internal.application.show.query.model.ShowDetailView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowDetailUseCase {

    private final ShowDetailReadRepository showDetailReadRepository;

    public record Input(Long showId) {
        public Input {
            RequiredInput.positiveId(showId, "showId");
        }
    }

    public record PerformerInfo(Long id, String name, String profileImageUrl) {
    }

    public record GradeInfo(Long id, String gradeCode, String gradeName, BigDecimal price, Integer sortOrder) {
    }

    public record PerformanceInfo(
            Long id,
            Long performanceNo,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime orderOpenTime,
            LocalDateTime orderCloseTime,
            BookingEntryResolver.EntryType entryType,
            boolean queueRequired,
            String redirectUrl,
            String queueEnterUrl
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
            List<GradeInfo> grades,
            List<PerformanceDateInfo> performanceDates
    ) {
    }

    public Output execute(final Input input) {
        return showDetailReadRepository.findShowDetail(input.showId())
                .map(view -> new Output(
                        view.id(), view.title(), view.subTitle(), view.info(), view.startDate(), view.endDate(),
                        view.runningMinutes(), view.viewCount(), view.likeCount(), view.bookingStatus(), view.saleType(),
                        view.saleStartDate(), view.saleEndDate(), view.image(), view.venue(), view.performer(),
                        view.genreNames(), view.grades(), view.performanceDates()
                ))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + input.showId()));
    }

}
