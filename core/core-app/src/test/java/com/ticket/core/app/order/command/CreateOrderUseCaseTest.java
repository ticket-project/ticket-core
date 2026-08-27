package com.ticket.core.app.order.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.app.order.command.CreateOrderUseCase;
import com.ticket.core.app.order.command.CreateOrderValidator;
import com.ticket.core.app.order.command.CreatePendingOrderTxService;
import com.ticket.core.domain.order.command.create.ValidatedOrderRequest;
import com.ticket.core.domain.order.command.create.RequestedSeatIds;
import com.ticket.core.domain.order.command.create.PendingOrderCreationResult;
import com.ticket.core.domain.order.command.create.HoldAllocator;
import com.ticket.core.domain.order.command.create.HoldAllocation;
import com.ticket.core.domain.hold.command.HoldCreationPostCommitNotifier;
import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CreateOrderUseCaseTest {

    @Mock
    private CreateOrderValidator validator;

    @Mock
    private HoldAllocator holdAllocator;

    @Mock
    private CreatePendingOrderTxService createPendingOrderTxService;

    @Mock
    private HoldCreationPostCommitNotifier holdCreationPostCommitNotifier;

    private CreateOrderUseCase createOrderUseCase;
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 19, 0);

    @BeforeEach
    void setUp() {
        createOrderUseCase = new CreateOrderUseCase(
                validator,
                holdAllocator,
                createPendingOrderTxService,
                holdCreationPostCommitNotifier,
                fixedClock
        );
    }

    @Test
    void 중복된_좌석_ID가_있으면_예외를_던진다() {
        final CreateOrderUseCase.Input input = new CreateOrderUseCase.Input(10L, List.of(3L, 1L, 3L), 20L, "admission-token");

        assertThatThrownBy(() -> createOrderUseCase.execute(input))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT));

        verifyNoInteractions(validator, holdAllocator, createPendingOrderTxService);
    }

    @Test
    void 좌석_ID가_비어있으면_예외를_던진다() {
        final CreateOrderUseCase.Input input = new CreateOrderUseCase.Input(10L, List.of(), 20L, "admission-token");

        assertThatThrownBy(() -> createOrderUseCase.execute(input))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT));

        verifyNoInteractions(validator, holdAllocator, createPendingOrderTxService);
    }

    @Test
    void 유효한_요청이면_hold와_주문을_생성한다() {
        final CreateOrderUseCase.Input input = new CreateOrderUseCase.Input(10L, List.of(7L, 3L), 20L, "admission-token");
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final PerformanceBookingPolicyView performance = createPerformance(5, 600);
        final List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class), mock(PerformanceSeat.class));
        final Hold hold = hold(seatIds.toList());
        final HoldAllocation allocation = new HoldAllocation(hold, seats);
        final Order order = order(hold);

        when(validator.validate(input, seatIds, FIXED_NOW))
                .thenReturn(new ValidatedOrderRequest(performance, allocation.performanceSeats()));
        when(holdAllocator.allocate(20L, 10L, seatIds, allocation.performanceSeats(), Duration.ofSeconds(600), FIXED_NOW))
                .thenReturn(allocation);
        when(createPendingOrderTxService.create(20L, 10L, Duration.ofSeconds(600), allocation))
                .thenReturn(new PendingOrderCreationResult(order, 99L));

        final CreateOrderUseCase.Output output = createOrderUseCase.execute(input);

        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.status()).isEqualTo(OrderState.PENDING);
        assertThat(output.expiresAt()).isEqualTo(hold.expiresAt());
        assertThat(output.remainingSeconds()).isEqualTo(600L);

        final InOrder inOrder = inOrder(validator, holdAllocator, createPendingOrderTxService, holdCreationPostCommitNotifier);
        inOrder.verify(validator).validate(input, seatIds, FIXED_NOW);
        inOrder.verify(holdAllocator).allocate(20L, 10L, seatIds, allocation.performanceSeats(), Duration.ofSeconds(600), FIXED_NOW);
        inOrder.verify(createPendingOrderTxService).create(20L, 10L, Duration.ofSeconds(600), allocation);
        inOrder.verify(holdCreationPostCommitNotifier).notify(99L);
    }

    @Test
    void 후처리_제출이_실패해도_커밋된_주문과_hold를_유지한다() {
        final CreateOrderUseCase.Input input = new CreateOrderUseCase.Input(10L, List.of(7L, 3L), 20L, "admission-token");
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final PerformanceBookingPolicyView performance = createPerformance(5, 600);
        final Hold hold = hold(seatIds.toList());
        final HoldAllocation allocation = new HoldAllocation(hold, List.of(mock(PerformanceSeat.class)));
        final Order order = order(hold);
        when(validator.validate(input, seatIds, FIXED_NOW))
                .thenReturn(new ValidatedOrderRequest(performance, allocation.performanceSeats()));
        when(holdAllocator.allocate(20L, 10L, seatIds, allocation.performanceSeats(), Duration.ofSeconds(600), FIXED_NOW))
                .thenReturn(allocation);
        when(createPendingOrderTxService.create(20L, 10L, Duration.ofSeconds(600), allocation))
                .thenReturn(new PendingOrderCreationResult(order, 99L));
        doThrow(new RuntimeException("queue failed")).when(holdCreationPostCommitNotifier).notify(99L);

        final CreateOrderUseCase.Output output = createOrderUseCase.execute(input);

        assertThat(output.orderKey()).isEqualTo("order-key");
        verify(holdAllocator, never()).release(allocation);
    }

    @Test
    void 주문_저장_트랜잭션이_실패하면_hold를_해제한다() {
        final CreateOrderUseCase.Input input = new CreateOrderUseCase.Input(10L, List.of(7L, 3L), 20L, "admission-token");
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final PerformanceBookingPolicyView performance = createPerformance(5, 600);
        final HoldAllocation allocation = new HoldAllocation(hold(seatIds.toList()), List.of(mock(PerformanceSeat.class)));

        when(validator.validate(input, seatIds, FIXED_NOW))
                .thenReturn(new ValidatedOrderRequest(performance, allocation.performanceSeats()));
        when(holdAllocator.allocate(20L, 10L, seatIds, allocation.performanceSeats(), Duration.ofSeconds(600), FIXED_NOW))
                .thenReturn(allocation);
        when(createPendingOrderTxService.create(20L, 10L, Duration.ofSeconds(600), allocation))
                .thenThrow(new RuntimeException("order failed"));

        assertThatThrownBy(() -> createOrderUseCase.execute(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("order failed");

        verify(holdAllocator).release(allocation);
    }

    @Test
    void hold_해제에_실패해도_원래_예외를_유지한다() {
        final CreateOrderUseCase.Input input = new CreateOrderUseCase.Input(10L, List.of(7L, 3L), 20L, "admission-token");
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final PerformanceBookingPolicyView performance = createPerformance(5, 600);
        final HoldAllocation allocation = new HoldAllocation(hold(seatIds.toList()), List.of(mock(PerformanceSeat.class)));
        final RuntimeException originalException = new RuntimeException("order failed");

        when(validator.validate(input, seatIds, FIXED_NOW))
                .thenReturn(new ValidatedOrderRequest(performance, allocation.performanceSeats()));
        when(holdAllocator.allocate(20L, 10L, seatIds, allocation.performanceSeats(), Duration.ofSeconds(600), FIXED_NOW))
                .thenReturn(allocation);
        when(createPendingOrderTxService.create(20L, 10L, Duration.ofSeconds(600), allocation))
                .thenThrow(originalException);
        doThrow(new RuntimeException("release failed"))
                .when(holdAllocator).release(allocation);

        assertThatThrownBy(() -> createOrderUseCase.execute(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("order failed")
                .satisfies(exception -> {
                    assertThat(exception.getSuppressed()).hasSize(1);
                    assertThat(exception.getSuppressed()[0].getMessage()).isEqualTo("release failed");
                });
    }

    @Test
    void execute는_DB_트랜잭션을_직접_시작하지_않는다() throws NoSuchMethodException {
        assertThat(CreateOrderUseCase.class
                .getDeclaredMethod("execute", CreateOrderUseCase.Input.class)
                .isAnnotationPresent(Transactional.class))
                .isFalse();
    }

    private Hold hold(final List<Long> seatIds) {
        return new Hold("hold-key", 20L, 10L, seatIds, FIXED_NOW.plusMinutes(10));
    }

    private Order order(final Hold hold) {
        return new Order(20L, 10L, "order-key", "hold-key", BigDecimal.valueOf(120000), hold.expiresAt());
    }

    private PerformanceBookingPolicyView createPerformance(final int maxCanHoldCount, final int holdTimeSeconds) {
        final LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        return new PerformanceBookingPolicyView(
                10L,
                now.minusHours(1),
                now.plusHours(3),
                maxCanHoldCount,
                holdTimeSeconds,
                null,
                null,
                null,
                null,
                null
        );
    }
}
