package com.ticket.bootstrap.venue;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

import com.ticket.bootstrap.support.BookingE2ETestSupport;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatLookupApi;

/**
 * venue 공개 계약이 실제 컨텍스트에서 어떻게 배선되는지를 고정한다.
 *
 * <p>공개 계약은 Aggregate별 use case가 하나씩 구현한다. 여기서 조용히 깨질 수 있는 것이 둘이다. 하나는 한 계약의 <b>구현이 둘</b>이 되어 주입이 모호해지는 것, 다른 하나는 use
 * case가 선언한 <b>읽기 전용 트랜잭션</b>이 proxy가 아니어서 실제로는 없는 것이다. 단위 테스트는 클래스를 직접 생성하므로 둘 다 잡지 못한다 — Spring 자신에게 물어본다.
 *
 * <p>두 계약은 Aggregate를 따라 빈 둘로 나뉘어 있다 — venue 표시값은 {@code VenueLookupService}, 물리 좌석은 {@code SeatLookupService}다. 계약별로
 * 주입 가능한 구현이 하나씩이라는 것과, 둘이 서로 다른 빈이라는 것을 여기서 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class VenuePublicApiWiringTest extends BookingE2ETestSupport {
    @Autowired
    private ApplicationContext context;

    @Test
    void venue_공개_계약의_구현_빈은_각각_하나고_Aggregate별로_다른_빈이다() {
        assertThat(context.getBeansOfType(VenueLookupApi.class)).hasSize(1);
        assertThat(context.getBeansOfType(VenueSeatLookupApi.class)).hasSize(1);
        assertThat(AopUtils.getTargetClass(context.getBean(VenueLookupApi.class))
                        .getName())
                .isEqualTo("com.ticket.venue.usecase.VenueLookupService");
        assertThat(AopUtils.getTargetClass(context.getBean(VenueSeatLookupApi.class))
                        .getName())
                .isEqualTo("com.ticket.venue.usecase.SeatLookupService");
        assertThat(context.getBean(VenueLookupApi.class)).isNotSameAs(context.getBean(VenueSeatLookupApi.class));
    }

    @Test
    void venue_조회는_읽기_전용_트랜잭션_안에서_끝난다() throws Exception {
        assertThat(readOnlyOf(VenueLookupApi.class, "getSummaries", Set.class)).isTrue();
        assertThat(readOnlyOf(VenueSeatLookupApi.class, "findAllSeatLayouts", long.class))
                .isTrue();
    }

    /** 트랜잭션 attribute가 선언돼 있어도 빈이 proxy가 아니면 실제로는 아무 경계도 없다. */
    @Test
    void venue_조회_빈은_실제로_proxy로_감싸진다() {
        assertThat(AopUtils.isAopProxy(context.getBean(VenueLookupApi.class))).isTrue();
        assertThat(AopUtils.isAopProxy(context.getBean(VenueSeatLookupApi.class)))
                .isTrue();
    }

    private boolean readOnlyOf(final Class<?> contract, final String methodName, final Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final Class<?> target = AopUtils.getTargetClass(context.getBean(contract));
        final Method method = target.getMethod(methodName, parameterTypes);
        final TransactionAttribute attribute =
                context.getBean(TransactionAttributeSource.class).getTransactionAttribute(method, target);
        assertThat(attribute)
                .as("%s.%s의 트랜잭션 attribute", target.getSimpleName(), methodName)
                .isNotNull();
        return attribute.isReadOnly();
    }
}
