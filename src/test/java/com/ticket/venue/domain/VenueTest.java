package com.ticket.venue.domain;

import com.ticket.venue.Region;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class VenueTest {

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
}
