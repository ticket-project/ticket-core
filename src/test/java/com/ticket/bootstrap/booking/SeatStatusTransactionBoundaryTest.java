package com.ticket.bootstrap.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.usecase.GetSeatStatusUseCase;
import com.ticket.bootstrap.support.BookingE2ETestSupport;

/**
 * 좌석 상태 조회의 트랜잭션 경계를 실제 컨텍스트에서 고정한다.
 *
 * <p>좌석 상태는 DB 판매 상태와 Redis 점유(selection·hold)를 합쳐 계산한다. <b>DB 읽기만 짧은 읽기 전용 트랜잭션 안에서 끝내고, Redis 조회는
 * 그 밖에서 한다</b> — 그래야 Redis 지연이 DB connection 보유 시간을 늘리지 않는다. 이 경계는 use case에
 * {@code @Transactional}을 붙이는 순간 조용히 무너지는데, 결과값은 그대로라 단위 테스트로는 드러나지 않는다.
 *
 * <p>그래서 결과가 아니라 <b>Spring이 실제로 무엇에 트랜잭션 advice를 적용하는지</b>를 본다. 단위 테스트는 클래스를 직접 생성하므로
 * {@code @Transactional}이 self-invocation으로 무력화돼도 통과한다. 여기서는 실제 컨텍스트가 만든 빈과 Spring 자신의 {@link
 * TransactionAttributeSource}에 물어본다.
 *
 * <p>경계를 <b>누가</b> 소유하는지는 바뀔 수 있다 — 예전에는 조회 port를 감싼 별도 component가, 지금은 조회 Repository 자신이 갖는다. 고정하는
 * 것은 소유자 이름이 아니라 "DB 읽기에는 읽기 전용 트랜잭션이 걸리고 use case에는 걸리지 않는다"는 성질이다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeatStatusTransactionBoundaryTest extends BookingE2ETestSupport {
    @Autowired private ApplicationContext context;

    @Test
    void DB_좌석_상태_읽기는_읽기_전용_트랜잭션_안에서_끝난다() throws Exception {
        final Class<?> adapter =
                AopUtils.getTargetClass(context.getBean(PerformanceSeatRepository.class));

        final TransactionAttribute attribute =
                transactionAttributeOf(adapter, "findSeatStates", Long.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.isReadOnly()).isTrue();
    }

    /** 트랜잭션 attribute가 선언돼 있어도 빈이 proxy가 아니면 실제로는 아무 경계도 없다. */
    @Test
    void 트랜잭션_경계를_소유한_빈은_실제로_proxy로_감싸진다() {
        assertThat(AopUtils.isAopProxy(context.getBean(PerformanceSeatRepository.class))).isTrue();
    }

    /** use case가 트랜잭션을 소유하면 Redis 조회까지 DB connection을 쥔 채로 하게 된다. */
    @Test
    void 좌석_상태_use_case는_트랜잭션을_소유하지_않는다() throws Exception {
        assertThat(
                        transactionAttributeOf(
                                GetSeatStatusUseCase.class,
                                "execute",
                                GetSeatStatusUseCase.Input.class))
                .isNull();
    }

    private TransactionAttribute transactionAttributeOf(
            final Class<?> type, final String methodName, final Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final Method method = type.getMethod(methodName, parameterTypes);
        return context.getBean(TransactionAttributeSource.class)
                .getTransactionAttribute(method, type);
    }
}
