package com.ticket.show.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.venue.api.Region;

@SuppressWarnings("NonAsciiCharacters")
class ShowListParamTest {
    @Test
    void 지역_문자열을_도메인_enum으로_바꾼다() {
        assertThat(ShowListParam.of("MUSICAL", "ROCK", "SEOUL", null).getRegion())
                .isEqualTo(Region.SEOUL);
    }

    /** 이 변환은 이전에 Spring enum 변환기가 하던 일이고 그 변환기는 공백을 지웠다. 같은 요청이 계속 통해야 한다. */
    @ParameterizedTest
    @ValueSource(strings = {" SEOUL", "SEOUL ", "  SEOUL  "})
    void 앞뒤_공백이_있어도_같은_지역으로_본다(final String region) {
        assertThat(ShowListParam.of(null, null, region, null).getRegion()).isEqualTo(Region.SEOUL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 지역이_비어있으면_필터하지_않는다(final String region) {
        assertThat(ShowListParam.of(null, null, region, null).getRegion()).isNull();
    }

    @Test
    void 알_수_없는_지역이면_invalid_input_예외를_던진다() {
        assertThatThrownBy(() -> ShowListParam.of(null, null, "NOWHERE", null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
