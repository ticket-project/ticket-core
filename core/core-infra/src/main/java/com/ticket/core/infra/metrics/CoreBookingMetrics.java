package com.ticket.core.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class CoreBookingMetrics {

    private static final String ORDER_CREATE_SUCCESS = "booking.order.create.success";
    private static final String ORDER_CREATE_FAILURE = "booking.order.create.failure";
    private static final String LOCK_ACQUIRE_FAILURE = "booking.distributed.lock.acquire.failure";

    private final MeterRegistry meterRegistry;
    private final Map<OutboxType, OutboxGaugeState> outboxGauges = new EnumMap<>(OutboxType.class);

    public CoreBookingMetrics(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.counter(ORDER_CREATE_SUCCESS);
        meterRegistry.counter(ORDER_CREATE_FAILURE);
        for (HoldOperation operation : HoldOperation.values()) {
            List.of("success", "failure")
                    .forEach(result -> holdCounter(operation, result));
        }
        for (OutboxType type : OutboxType.values()) {
            OutboxGaugeState state = new OutboxGaugeState();
            outboxGauges.put(type, state);
            registerOutboxGauges(type, state);
            observationFailureCounter(type);
        }
    }

    public void recordOrderCreate(final boolean success) {
        meterRegistry.counter(success ? ORDER_CREATE_SUCCESS : ORDER_CREATE_FAILURE).increment();
    }

    public void recordHold(final HoldOperation operation, final boolean success) {
        holdCounter(operation, success ? "success" : "failure").increment();
    }

    public void recordLockAcquireFailure(
            final String operation,
            final LockFailureReason reason
    ) {
        meterRegistry.counter(
                LOCK_ACQUIRE_FAILURE,
                "operation", normalizeOperation(operation),
                "reason", reason.tagValue()
        ).increment();
    }

    public void updateOutbox(
            final OutboxType type,
            final long pendingCount,
            final long failedCount,
            final long oldestAgeMillis
    ) {
        OutboxGaugeState state = outboxGauges.get(type);
        state.pendingCount().set(nonNegative(pendingCount));
        state.failedCount().set(nonNegative(failedCount));
        state.oldestAgeMillis().set(nonNegative(oldestAgeMillis));
    }

    public void recordOutboxObservationFailure(final OutboxType type) {
        observationFailureCounter(type).increment();
    }

    private void registerOutboxGauges(final OutboxType type, final OutboxGaugeState state) {
        Gauge.builder("booking.outbox.pending", state.pendingCount(), AtomicLong::get)
                .tag("type", type.tagValue())
                .register(meterRegistry);
        Gauge.builder("booking.outbox.failed", state.failedCount(), AtomicLong::get)
                .tag("type", type.tagValue())
                .register(meterRegistry);
        Gauge.builder(
                        "booking.outbox.oldest.age",
                        state.oldestAgeMillis(),
                        value -> value.doubleValue() / 1_000.0
                )
                .tag("type", type.tagValue())
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    private Counter holdCounter(final HoldOperation operation, final String result) {
        return meterRegistry.counter("booking.hold." + operation.tagValue(), "result", result);
    }

    private Counter observationFailureCounter(final OutboxType type) {
        return meterRegistry.counter(
                "booking.outbox.observation.failure",
                "type", type.tagValue(),
                "reason", "outbox_metric_query_failure"
        );
    }

    private String normalizeOperation(final String operation) {
        return operation == null || operation.isBlank() ? "unspecified" : operation;
    }

    private long nonNegative(final long value) {
        return Math.max(0L, value);
    }

    public enum HoldOperation {
        CREATE("create"),
        RELEASE("release"),
        EXPIRE("expire");

        private final String tagValue;

        HoldOperation(final String tagValue) {
            this.tagValue = tagValue;
        }

        private String tagValue() {
            return tagValue;
        }
    }

    public enum OutboxType {
        HOLD_CREATION("hold_creation"),
        HOLD_RELEASE("hold_release");

        private final String tagValue;

        OutboxType(final String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    public enum LockFailureReason {
        CONTENDED("lock_not_acquired"),
        INTERRUPTED("lock_wait_interrupted");

        private final String tagValue;

        LockFailureReason(final String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    private record OutboxGaugeState(
            AtomicLong pendingCount,
            AtomicLong failedCount,
            AtomicLong oldestAgeMillis
    ) {

        private OutboxGaugeState() {
            this(new AtomicLong(), new AtomicLong(), new AtomicLong());
        }
    }
}
