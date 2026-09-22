package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
class ShowListParamTest {
    @Test
    void 지역_문자열을_도메인_enum으로_바꾼다() {
        assertThat(ShowListParam.of("MUSICAL", "ROCK", "SEOUL", null).getRegion())
                .isEqualTo("SEOUL");
    }

    /** 이 변환은 이전에 Spring enum 변환기가 하던 일이고 그 변환기는 공백을 지웠다. 같은 요청이 계속 통해야 한다. */
    @ParameterizedTest
    @ValueSource(strings = {" SEOUL", "SEOUL ", "  SEOUL  "})
    void 앞뒤_공백이_있어도_같은_지역으로_본다(final String region) {
        assertThat(ShowListParam.of(null, null, region, null).getRegion()).isEqualTo("SEOUL");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 지역이_비어있으면_필터하지_않는다(final String region) {
        assertThat(ShowListParam.of(null, null, region, null).getRegion()).isNull();
    }

    /** 정규화가 {@code of()}에만 있으면 생성자로 만든 같은 인자의 객체가 다른 뜻이 된다. 실제로 테스트 대부분이 생성자를 직접 부른다. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " SEOUL ", "SEOUL"})
    void 생성자로_만들어도_of와_같은_지역이_된다(final String region) {
        assertThat(new ShowListParam(null, null, region, null).getRegion())
                .isEqualTo(ShowListParam.of(null, null, region, null).getRegion());
    }
}
