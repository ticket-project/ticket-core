package com.ticket.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.ticket.booking.concurrency.DistributedLock;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.usecase.ExpirePendingOrdersUseCase;
import com.ticket.booking.order.usecase.OrderExpirationTrigger;
import com.ticket.booking.order.usecase.StartBookingUseCase;
import com.ticket.bootstrap.support.BookingE2ETestSupport;

/**
 * 실행 모듈이 전체 컨텍스트를 실제로 조립하는지 확인한다.
 *
 * <p>단위 테스트는 각 클래스를 직접 생성하므로 빈 배선이 깨져도 통과한다. 모듈 사이로 빈을 옮기는 변경에서 기동 실패를 잡아내려면 컨텍스트를 한 번은 통째로 띄워봐야 한다.
 *
 * <p>기동 설정은 {@link BookingE2ETestSupport}가 소유한다. 예매 E2E 테스트와 같은 설정을 써야 Spring 컨텍스트가 하나로 재사용된다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ApplicationContextLoadTest extends BookingE2ETestSupport {
    @Autowired
    private ApplicationContext context;

    @Test
    void 실행_모듈이_예매_핵심_빈을_한_컨텍스트로_조립한다() {
        assertThat(context.getBean(StartBookingUseCase.class)).isNotNull();
        assertThat(context.getBean(OrderRepository.class)).isNotNull();
        assertThat(context.getBean(DistributedLock.class)).isNotNull();
        // 아래 둘은 package-private이라 타입으로 참조할 수 없다. component scan이 붙이는 기본 bean 이름으로 찾는다.
        assertThat(context.containsBean("bookingEventListeners")).isTrue();
        assertThat(context.containsBean("eventPublicationMaintenance")).isTrue();
    }

    /** 도메인 Repository는 포트이고 실제 빈은 저장 기술 어댑터다. 어댑터가 빠지면 기동에서 바로 드러난다. */
    @Test
    void 도메인_Repository는_저장_기술_어댑터로_구현된다() {
        assertThat(context.getBean(OrderRepository.class).getClass().getName())
                .startsWith("com.ticket.booking.order.persistence.");
        assertThat(context.getBean(HoldStore.class).getClass().getName())
                .startsWith("com.ticket.booking.hold.persistence.");
        assertThat(context.getBean(DistributedLock.class).getClass().getName())
                .startsWith("com.ticket.booking.concurrency.redis.");
    }

    @Test
    void worker가_켜져_있으면_background_트리거가_등록된다() {
        assertThat(context.getBeansOfType(OrderExpirationTrigger.class)).hasSize(1);
        assertThat(context.getBean(ExpirePendingOrdersUseCase.class)).isNotNull();
    }
}
