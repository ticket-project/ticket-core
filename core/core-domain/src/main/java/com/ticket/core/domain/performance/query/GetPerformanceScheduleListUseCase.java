package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.show.model.Show;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceScheduleListUseCase {

    private final PerformanceFinder performanceFinder;
    private final PerformanceRepository performanceRepository;

    public record Input(Long performanceId) {}

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
        final Performance findPerformance = performanceFinder.findById(input.performanceId());

        final Show show = findPerformance.getShow();
        if (show == null) {
            throw new CoreException(
                    ErrorType.NOT_FOUND_DATA,
                    "회차에 연결된 공연을 찾을 수 없습니다. id=" + input.performanceId()
            );
        }

        final List<PerformanceScheduleItem> scheduleItems = performanceRepository
                .findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(show.getId())
                .stream()
                .map(performance -> new PerformanceScheduleItem(
                        performance.getId(),
                        performance.getPerformanceNo(),
                        performance.getStartTime()
                ))
                .toList();

        return new Output(show.getId(), findPerformance.getId(), scheduleItems);
    }
}
