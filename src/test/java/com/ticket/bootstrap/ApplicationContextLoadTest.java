package com.ticket.bootstrap;

import com.ticket.bootstrap.support.BookingE2ETestSupport;
import com.ticket.bootstrap.worker.OrderExpirationTrigger;
import com.ticket.booking.internal.application.lock.LockManager;
import com.ticket.booking.internal.application.order.command.CreateOrderUseCase;
import com.ticket.booking.internal.application.order.command.ExpirePendingOrdersUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실행 모듈이 전체 컨텍스트를 실제로 조립하는지 확인한다.
 *
 * <p>단위 테스트는 각 클래스를 직접 생성하므로 빈 배선이 깨져도 통과한다. 모듈 사이로 빈을 옮기는
 * 변경에서 기동 실패를 잡아내려면 컨텍스트를 한 번은 통째로 띄워봐야 한다.
 *
 * <p>기동 설정은 {@link BookingE2ETestSupport}가 소유한다. 예매 E2E 테스트와 같은 설정을 써야
 * Spring 컨텍스트가 하나로 재사용된다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ApplicationContextLoadTest extends BookingE2ETestSupport {

    @Autowired
    private ApplicationContext context;

    @Test
    void 실행_모듈이_네_모듈을_한_컨텍스트로_조립한다() {
        assertThat(context.getBean(CreateOrderUseCase.class)).isNotNull();
        assertThat(beanOf("com.ticket.booking.internal.domain.order.repository.OrderRepository")).isNotNull();
        assertThat(context.getBean(LockManager.class)).isNotNull();
        assertThat(beanOf("com.ticket.booking.internal.application.BookingEventListeners")).isNotNull();
        assertThat(beanOf("com.ticket.configuration.EventPublicationMaintenance")).isNotNull();
    }

    /**
     * 도메인 Repository는 포트이고 실제 빈은 infra 어댑터다. 어댑터가 빠지면 기동에서 바로 드러난다.
     */
    @Test
    void 도메인_Repository는_infra_어댑터로_구현된다() {
        assertThat(beanOf("com.ticket.booking.internal.domain.order.repository.OrderRepository").getClass().getName())
                .startsWith("com.ticket.booking.internal.infrastructure.");
        assertThat(beanOf("com.ticket.booking.internal.domain.hold.store.HoldStore").getClass().getName())
                .startsWith("com.ticket.booking.internal.infrastructure.");
        assertThat(context.getBean(LockManager.class).getClass().getName())
                .startsWith("com.ticket.booking.internal.infrastructure.");
    }

    /**
     * bootstrap은 도메인을 컴파일 타임에 보지 않는다. 런타임 클래스패스에만 있으므로 이름으로 찾는다.
     */
    private Object beanOf(final String typeName) {
        try {
            return context.getBean(Class.forName(typeName));
        } catch (final ClassNotFoundException exception) {
            throw new IllegalStateException("런타임 클래스패스에 없습니다: " + typeName, exception);
        }
    }

    @Test
    void worker가_켜져_있으면_background_트리거가_등록된다() {
        assertThat(context.getBeansOfType(OrderExpirationTrigger.class)).hasSize(1);
        assertThat(context.getBean(ExpirePendingOrdersUseCase.class)).isNotNull();
    }
}
