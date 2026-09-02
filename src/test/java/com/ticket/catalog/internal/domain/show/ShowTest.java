package com.ticket.catalog.internal.domain.show;

import com.ticket.catalog.internal.domain.show.BookingStatus;
import com.ticket.catalog.internal.domain.show.SaleType;
import com.ticket.catalog.internal.domain.show.Show;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class ShowTest {

    @Test
    void 판매시작시간이_없으면_예매상태는_닫힘이다() {
        //given
        //when
        Show show = createShow(null, null);

        //then
        assertThat(show.getBookingStatus(LocalDateTime.now())).isEqualTo(BookingStatus.CLOSED);
    }

    @Test
    void 판매시작전이면_예매상태는_오픈전이다() {
        //given
        //when
        Show show = createShow(LocalDateTime.now().plusMinutes(10), LocalDateTime.now().plusHours(1));

        //then
        assertThat(show.getBookingStatus(LocalDateTime.now())).isEqualTo(BookingStatus.BEFORE_OPEN);
    }

    @Test
    void 판매기간중이면_예매상태는_판매중이다() {
        //given
        //when
        Show show = createShow(LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));

        //then
        assertThat(show.getBookingStatus(LocalDateTime.now())).isEqualTo(BookingStatus.ON_SALE);
    }

    @Test
    void 판매시작시각과_같으면_예매상태는_판매중이다() {
        //given
        //when
        LocalDateTime now = LocalDateTime.now();
        Show show = createShow(now, now.plusMinutes(10));

        //then
        assertThat(show.getBookingStatus(now)).isEqualTo(BookingStatus.ON_SALE);
    }

    @Test
    void 판매종료시각과_같으면_예매상태는_판매중이다() {
        //given
        //when
        LocalDateTime now = LocalDateTime.now();
        Show show = createShow(now.minusMinutes(10), now);

        //then
        assertThat(show.getBookingStatus(now)).isEqualTo(BookingStatus.ON_SALE);
    }

    @Test
    void 판매종료후면_예매상태는_닫힘이다() {
        //given
        //when
        Show show = createShow(LocalDateTime.now().minusHours(1), LocalDateTime.now().minusMinutes(10));

        //then
        assertThat(show.getBookingStatus(LocalDateTime.now())).isEqualTo(BookingStatus.CLOSED);
    }

    private Show createShow(final LocalDateTime saleStartDate, final LocalDateTime saleEndDate) {
        return new Show(
                "공연",
                "부제",
                "소개",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                0L,
                SaleType.GENERAL,
                saleStartDate,
                saleEndDate,
                "image",
                null,
                null,
                120
        );
    }
}
