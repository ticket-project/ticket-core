package com.ticket.core.app.performance.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.show.model.Show;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceScheduleListUseCase {

    private final PerformanceRepository performanceRepository;

    public record Input(Long performanceId) {
        public Input {
            RequiredInput.positiveId(performanceId, "performanceId");
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
        final Performance findPerformance = performanceRepository.getWithQueuePolicyById(input.performanceId());

        final Show show = findPerformance.getShow();
        if (show == null) {
            throw new CoreException(
                    ApplicationErrorType.DATA_NOT_FOUND,
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
