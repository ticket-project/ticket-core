package com.ticket.booking.internal.application.order.query;

import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.application.order.query.model.OrderDetailRow;
import com.ticket.booking.internal.exception.OrderNotOwnedException;
import com.ticket.catalog.PerformanceSummary;
import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSeatMapEntry;
import com.ticket.catalog.ShowSummary;
import com.ticket.error.NotFoundException;
import com.ticket.identity.MemberLookup;
import com.ticket.identity.MemberProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetOrderDetailUseCaseTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-15T10:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private OrderReadRepository orderReadRepository;

    @Mock
    private ShowLookup showLookup;

    @Mock
    private MemberLookup memberLookup;

    private GetOrderDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOrderDetailUseCase(orderReadRepository, showLookup, memberLookup, FIXED_CLOCK);
    }

    @Test
    void 단일_조회결과를_주문상세로_조합한다() {
        when(orderReadRepository.findDetailRows("order-key", 1L)).thenReturn(List.of(row()));
        when(memberLookup.getProfile(1L)).thenReturn(new MemberProfile(1L, "홍길동", "user@example.com"));
        when(showLookup.getPerformanceSummaries(Set.of(10L))).thenReturn(Map.of(
                10L, new PerformanceSummary(10L, 100L, 1L, LocalDateTime.of(2026, 3, 20, 19, 30))
        ));
        when(showLookup.getSummaries(Set.of(100L))).thenReturn(Map.of(
                100L, new ShowSummary(100L, "뮤지컬", "show.png", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), "올림픽홀")
        ));
        when(showLookup.getSeatMap(100L)).thenReturn(List.of(
                new ShowSeatMapEntry(42L, 1, "A", "10", "7", 0, 0, "VIP", "VIP", BigDecimal.TEN, 1)
        ));

        GetOrderDetailUseCase.Output output = useCase.execute(
                new GetOrderDetailUseCase.Input("order-key", 1L)
        );

        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.show().title()).isEqualTo("뮤지컬");
        assertThat(output.performance().venueName()).isEqualTo("올림픽홀");
        assertThat(output.performance().performanceNo()).isEqualTo(1L);
        assertThat(output.booker().email()).isEqualTo("user@example.com");
        assertThat(output.price().ticketAmount()).isEqualByComparingTo("120000");
        assertThat(output.tickets().count()).isEqualTo(1);
        assertThat(output.tickets().seats().getFirst().label()).isEqualTo("1F A구역 10열 7번");
        assertThat(output.remainingSeconds()).isEqualTo(600L);
    }

    @Test
    void 본인_주문이_없으면_권한예외를_던진다() {
        when(orderReadRepository.findDetailRows("missing", 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(new GetOrderDetailUseCase.Input("missing", 1L)))
                .isInstanceOf(OrderNotOwnedException.class);

        verifyNoInteractions(memberLookup, showLookup);
    }

    @Test
    void 탈퇴한_회원의_주문이면_조회하지_않는다() {
        when(orderReadRepository.findDetailRows("order-key", 1L)).thenReturn(List.of(row()));
        when(memberLookup.getProfile(1L)).thenThrow(new NotFoundException());

        assertThatThrownBy(() -> useCase.execute(new GetOrderDetailUseCase.Input("order-key", 1L)))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(showLookup);
    }

    private OrderDetailRow row() {
        return new OrderDetailRow(
                "order-key",
                OrderState.PENDING,
                LocalDateTime.of(2026, 3, 15, 19, 10),
                1L,
                10L,
                501L,
                42L,
                BigDecimal.valueOf(120000)
        );
    }
}
