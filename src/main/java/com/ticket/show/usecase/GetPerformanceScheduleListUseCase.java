package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceScheduleListUseCase {
    private final PerformanceRepository performanceRepository;

    public record Input(Long performanceId) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
        }
    }

    public record Output(
            Long showId, Long selectedPerformanceId, List<PerformanceScheduleItem> schedules) {}

    public record PerformanceScheduleItem(
            Long performanceId, Long performanceNo, java.time.LocalDateTime startTime) {}

    public Output execute(final Input input) {
        final Performance findPerformance =
                performanceRepository
                        .findById(input.performanceId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "공연을 찾을 수 없습니다. id=" + input.performanceId()));

        final Long showId = findPerformance.getShowId();

        final List<PerformanceScheduleItem> scheduleItems =
                performanceRepository
                        .findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(showId)
                        .stream()
                        .map(
                                performance ->
                                        new PerformanceScheduleItem(
                                                performance.getId(),
                                                performance.getPerformanceNo(),
                                                performance.getStartTime()))
                        .toList();

        return new Output(showId, findPerformance.getId(), scheduleItems);
    }
}
