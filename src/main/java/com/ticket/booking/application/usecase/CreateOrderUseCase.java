package com.ticket.booking.application.usecase;

import com.ticket.booking.application.CreateOrderValidator;
import com.ticket.booking.application.CreatePendingOrderTransactionService;
import com.ticket.booking.application.ValidatedOrderRequest;

import com.ticket.booking.application.LockKey;
import com.ticket.booking.application.LockManager;
import com.ticket.booking.application.LockOptions;
import com.ticket.booking.domain.OrderRemainingTime;
import com.ticket.booking.domain.HoldAllocation;
import com.ticket.booking.domain.HoldAllocator;
import com.ticket.booking.domain.PendingOrderCreationResult;
import com.ticket.booking.domain.RequestedSeatIds;
import com.ticket.booking.domain.OrderState;
import com.ticket.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private static final LockOptions START_ORDER_LOCK = LockOptions.defaults()
            .withFailureMessage("주문 시작 처리 중입니다. 잠시 후 다시 시도해 주세요.");

    private final LockManager lockManager;
    private final CreateOrderValidator validator;
    private final HoldAllocator holdAllocator;
    private final CreatePendingOrderTransactionService createPendingOrderTransactionService;
    private final Clock clock;

    /**
     * seatIds의 null·빈 목록·중복은 도메인 불변식이라 {@link RequestedSeatIds}가 판정한다.
     * 여기서 다시 검사하지 않는다.
     */
    public record Input(Long performanceId, List<Long> seatIds, Long memberId, String admissionToken) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }
    }

    public record Output(
            String orderKey,
            OrderState status,
            LocalDateTime expiresAt,
            long remainingSeconds
    ) {}

    /**
     * 같은 회원과 회차의 주문 시작을 직렬화한 뒤 좌석을 선점하고 PENDING 주문을 만든다.
     *
     * <p>좌석 락은 Redis hold를 만드는 구간에만 건다. DB 트랜잭션 동안 좌석 락을 쥐고 있으면
     * connection 경합이 좌석 경합으로 번진다.
     */
    public Output execute(final Input input) {
        return lockManager.withLock(
                List.of(LockKey.orderStart(input.memberId(), input.performanceId())),
                START_ORDER_LOCK,
                () -> createOrder(input)
        );
    }

    private Output createOrder(final Input input) {
        final RequestedSeatIds requestedSeatIds = RequestedSeatIds.from(input.seatIds());
        final LocalDateTime now = LocalDateTime.now(clock);
        final ValidatedOrderRequest validated = validator.validate(input, requestedSeatIds, now);
        final Duration holdDuration = validated.policy().holdDuration();
        final List<LockKey> seatLocks = LockKey.seats(input.performanceId(), requestedSeatIds.toList());

        final HoldAllocation allocation = lockManager.withLock(
                seatLocks,
                LockOptions.defaults(),
                () -> holdAllocator.allocate(
                        input.memberId(),
                        input.performanceId(),
                        requestedSeatIds,
                        validated.performanceSeats(),
                        holdDuration,
                        now
                )
        );

        final PendingOrderCreationResult creationResult;
        try {
            creationResult = createPendingOrderTransactionService.create(
                    input.memberId(),
                    input.performanceId(),
                    holdDuration,
                    allocation,
                    validated.saleSnapshot()
            );
        } catch (final RuntimeException e) {
            releaseHold(seatLocks, allocation, e);
            throw e;
        }
        return new Output(
                creationResult.order().getOrderKey(),
                OrderState.PENDING,
                allocation.expiresAt(),
                OrderRemainingTime.seconds(OrderState.PENDING, allocation.expiresAt(), LocalDateTime.now(clock))
        );
    }

    private void releaseHold(
            final List<LockKey> seatLocks,
            final HoldAllocation allocation,
            final RuntimeException originalException
    ) {
        try {
            lockManager.withLock(seatLocks, LockOptions.defaults(), () -> holdAllocator.release(allocation));
        } catch (final RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
            log.warn("hold 해제에 실패했습니다. holdKey={}", allocation.holdKey(), releaseException);
        }
    }
}
