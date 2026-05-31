package com.ticket.core.domain.show.query;

import com.ticket.core.domain.performance.query.BookingEntryResolver;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.domain.show.BookingStatus;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
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

    private final ShowFinder showFinder;

    public record Input(Long showId) {
        public Input {
            if (showId == null) {
                throw new CoreException(ErrorType.INVALID_REQUEST, "showId는 필수입니다.");
            }
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
        validateInput(input);
        return showFinder.findShowDetail(input.showId());
    }

    private void validateInput(final Input input) {
        if (input == null) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "showId는 필수입니다.");
        }
    }
}
