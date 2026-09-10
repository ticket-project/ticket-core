package com.ticket.show.performance.application.usecase;

import com.ticket.show.performance.domain.Performance;
import com.ticket.show.performance.domain.PerformanceRepository;
import com.ticket.show.catalog.domain.Show;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceScheduleListUseCase {

    private final PerformanceRepository performanceRepository;

    public record Input(Long performanceId) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
        }
    }

    public record Output(
            Long showId,
            Long selectedPerformanceId,
            List<PerformanceScheduleItem> schedules
    ) {}

    public record PerformanceScheduleItem(
            Long performanceId,
            Long performanceNo,
            java.time.LocalDateTime startTime
    ) {}

    public Output execute(final Input input) {
        final Performance findPerformance = performanceRepository.findById(input.performanceId())
                .orElseThrow(() -> new NotFoundException(
                        "공연을 찾을 수 없습니다. id=" + input.performanceId()));

        final Long showId = findPerformance.getShowId();

        final List<PerformanceScheduleItem> scheduleItems = performanceRepository
                .findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(showId)
                .stream()
                .map(performance -> new PerformanceScheduleItem(
                        performance.getId(),
                        performance.getPerformanceNo(),
                        performance.getStartTime()
                ))
                .toList();

        return new Output(showId, findPerformance.getId(), scheduleItems);
    }
}
