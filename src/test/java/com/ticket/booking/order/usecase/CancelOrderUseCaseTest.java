package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;


@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CancelOrderUseCaseTest {
    @Mock
    private CancelOrderTransactionService cancelOrderTransactionService;

    @Test
    void execute는_트랜잭션_없이_다른_module_공개_API를_호출한다() throws NoSuchMethodException {
        Transactional transactional = CancelOrderUseCase.class
                .getDeclaredMethod("execute", CancelOrderUseCase.Input.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }

    @Test
    void 회원_활성_확인_후_booking_local_취소를_위임한다() {
        final CancelOrderUseCase useCase = new CancelOrderUseCase(cancelOrderTransactionService);

        useCase.execute(new CancelOrderUseCase.Input("order-key", 1L));

        verify(cancelOrderTransactionService).cancel("order-key", 1L);
    }
}
