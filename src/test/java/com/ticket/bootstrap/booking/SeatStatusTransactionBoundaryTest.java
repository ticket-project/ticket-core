package com.ticket.bootstrap.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

import com.ticket.booking.seat.usecase.GetOccupiedSeatsUseCase;
import com.ticket.booking.seat.usecase.GetSeatStatusUseCase;
import com.ticket.bootstrap.support.BookingE2ETestSupport;

/**
 * 좌석 상태 조회의 트랜잭션 경계를 실제 컨텍스트에서 고정한다.
 *
 * <p>좌석 상태는 DB 판매 상태와 Redis 점유(selection·hold)를 합쳐 계산한다. <b>DB 읽기는 Redis 조회 전에 끝내고 connection을 돌려준다</b> — 그래야 Redis 지연이
 * DB connection 보유 시간을 늘리지 않는다. 지금 DB 읽기는 조회 한 번이라 트랜잭션 없이 자동 커밋으로 끝난다. 이 경계는 use case에 {@code @Transactional}을 붙이거나
 * OSIV를 켜는 순간 조용히 무너지는데, 결과값은 그대로라 단위 테스트로는 드러나지 않는다.
 *
 * <p>그래서 결과가 아니라 실제 컨텍스트가 use case에 트랜잭션 advice를 적용하는지({@link TransactionAttributeSource}), OSIV 인터셉터가 있는지를 본다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeatStatusTransactionBoundaryTest extends BookingE2ETestSupport {
    @Autowired
    private ApplicationContext context;

    /** OSIV는 트랜잭션 없이도 요청 끝까지 connection을 쥐므로 use case 검사를 통과하면서도 경계를 무너뜨린다 — 그래서 OSIV 인터셉터가 없는지를 따로 본다. */
    @Test
    void OSIV가_꺼져_있어_요청이_connection을_끝까지_쥐지_않는다() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class))
                .isEmpty();
    }

    /** use case가 트랜잭션을 소유하면 Redis 조회까지 DB connection을 쥔 채로 하게 된다. */
    @Test
    void 좌석_상태_use_case는_트랜잭션을_소유하지_않는다() throws Exception {
        assertThat(transactionAttributeOf(GetSeatStatusUseCase.class, "execute", GetSeatStatusUseCase.Input.class))
                .isNull();
        assertThat(transactionAttributeOf(
                        GetOccupiedSeatsUseCase.class, "execute", GetOccupiedSeatsUseCase.Input.class))
                .isNull();
    }

    private TransactionAttribute transactionAttributeOf(
            final Class<?> type, final String methodName, final Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final Method method = type.getMethod(methodName, parameterTypes);
        return context.getBean(TransactionAttributeSource.class).getTransactionAttribute(method, type);
    }
}
