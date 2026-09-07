package com.ticket.show.domain.show;

import com.ticket.show.domain.show.Category;
import com.ticket.venue.Region;
import com.ticket.show.domain.show.Performer;
import com.ticket.venue.domain.venue.Venue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

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
}
