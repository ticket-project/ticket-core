package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.command.HoldCreationPostCommitNotifier;
import com.ticket.core.domain.hold.model.HoldSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import static com.ticket.core.infra.order.BookingBackgroundTaskConfig.BOOKING_BACKGROUND_TASK_EXECUTOR;

@Slf4j
@Component
public class AsyncHoldCreationPostCommitNotifier implements HoldCreationPostCommitNotifier {

    private final HoldCreationPostCommitProcessor processor;
    private final TaskExecutor taskExecutor;

    public AsyncHoldCreationPostCommitNotifier(
            final HoldCreationPostCommitProcessor processor,
            @Qualifier(BOOKING_BACKGROUND_TASK_EXECUTOR) final TaskExecutor taskExecutor
    ) {
        this.processor = processor;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void notify(final HoldSnapshot snapshot) {
        try {
            taskExecutor.execute(() -> process(snapshot));
        } catch (final TaskRejectedException e) {
            log.warn("주문 생성 후처리 큐가 가득 찼습니다. holdKey={}", snapshot.holdKey());
        }
    }

    private void process(final HoldSnapshot snapshot) {
        try {
            processor.process(snapshot);
        } catch (final RuntimeException e) {
            log.error("주문 생성 후처리에 실패했습니다. holdKey={}", snapshot.holdKey(), e);
        }
    }
}
