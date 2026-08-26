package com.ticket.core.domain.show.entity;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.model.Category;
import com.ticket.core.domain.show.mapping.ShowGrade;
import com.ticket.core.domain.show.mapping.ShowSeat;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.domain.show.performer.Performer;
import com.ticket.core.domain.show.venue.Venue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class EntityCreationFactoryTest {

    @Test
    void 공연장_정적팩토리로_필수정보를_생성한다() {
        Venue venue = Venue.create(
                "올림픽홀",
                "서울시 송파구",
                Region.SEOUL,
                "상세 주소",
                "12345",
                BigDecimal.valueOf(37.5),
                BigDecimal.valueOf(127.0),
                "02-0000-0000",
                "https://example.com/venue.png",
                1000,
                800,
                12.0,
                2.0,
                3.0
        );

        assertThat(venue.getName()).isEqualTo("올림픽홀");
        assertThat(venue.getAddress()).isEqualTo("서울시 송파구");
        assertThat(venue.getRegion()).isEqualTo(Region.SEOUL);
        assertThat(venue.getViewBoxWidth()).isEqualTo(1000);
        assertThat(venue.getGapY()).isEqualTo(3.0);
    }

    @Test
    void 카테고리와_출연자를_정적팩토리로_생성한다() {
        Category category = Category.of("CONCERT", "콘서트");
        Performer performer = Performer.create("아이유", "https://example.com/performer.png");

        assertThat(category.getCode()).isEqualTo("CONCERT");
        assertThat(category.getName()).isEqualTo("콘서트");
        assertThat(performer.getName()).isEqualTo("아이유");
        assertThat(performer.getProfileImageUrl()).isEqualTo("https://example.com/performer.png");
    }

    @Test
    void 공연등급과_공연좌석을_연결_정적팩토리로_생성한다() {
        Venue venue = Venue.create(
                "공연장",
                "주소",
                Region.SEOUL,
                "상세",
                "12345",
                BigDecimal.valueOf(37.5),
                BigDecimal.valueOf(127.0),
                "02-0000-0000",
                "https://example.com/venue.png",
                1000,
                800,
                12.0,
                2.0,
                2.0
        );
        Show show = new Show(
                "공연",
                "부제",
                "소개",
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 4, 20),
                10L,
                SaleType.GENERAL,
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 19, 23, 59),
                "image",
                venue,
                null,
                120
        );
        Seat seat = new Seat("A", "3", "5", 1, 10.0, 20.0);

        ShowGrade showGrade = ShowGrade.link(show, "VIP", "VIP석", BigDecimal.valueOf(150000), 1);
        ShowSeat showSeat = ShowSeat.link(show, seat, showGrade);

        assertThat(showGrade.getShow()).isSameAs(show);
        assertThat(showGrade.getGradeCode()).isEqualTo("VIP");
        assertThat(showGrade.getPrice()).isEqualByComparingTo("150000");
        assertThat(showSeat.getShow()).isSameAs(show);
        assertThat(showSeat.getSeat()).isSameAs(seat);
        assertThat(showSeat.getShowGrade()).isSameAs(showGrade);
    }

    @Test
    void 공연좌석_연결시_공연과_등급의_공연이_다르면_예외를_던진다() {
        Venue venue = Venue.create(
                "공연장",
                "주소",
                Region.SEOUL,
                "상세",
                "12345",
                BigDecimal.valueOf(37.5),
                BigDecimal.valueOf(127.0),
                "02-0000-0000",
                "https://example.com/venue.png",
                1000,
                800,
                12.0,
                2.0,
                2.0
        );
        Show show = new Show(
                "공연",
                "부제",
                "소개",
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 4, 20),
                10L,
                SaleType.GENERAL,
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 19, 23, 59),
                "image",
                venue,
                null,
                120
        );
        Show otherShow = new Show(
                "다른공연",
                "부제",
                "소개",
                LocalDate.of(2026, 3, 21),
                LocalDate.of(2026, 4, 21),
                20L,
                SaleType.GENERAL,
                LocalDateTime.of(2026, 3, 2, 10, 0),
                LocalDateTime.of(2026, 3, 20, 23, 59),
                "image2",
                venue,
                null,
                120
        );
        Seat seat = new Seat("A", "3", "5", 1, 10.0, 20.0);
        ShowGrade otherShowGrade = ShowGrade.link(otherShow, "VIP", "VIP석", BigDecimal.valueOf(150000), 1);

        assertThatThrownBy(() -> ShowSeat.link(show, seat, otherShowGrade))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT));
    }
}
