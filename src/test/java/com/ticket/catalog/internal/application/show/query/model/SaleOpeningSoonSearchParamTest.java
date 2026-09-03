package com.ticket.catalog.internal.application.show.query.model;

import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class SaleOpeningSoonSearchParamTest {

    private static final LocalDateTime EARLY = LocalDateTime.of(2026, 4, 1, 0, 0);
    private static final LocalDateTime LATE = LocalDateTime.of(2026, 4, 30, 0, 0);

    @Test
    void 판매시작_From이_To보다_늦으면_invalid_input_예외를_던진다() {
        assertThatThrownBy(() -> param(LATE, EARLY, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 판매종료_From이_To보다_늦으면_invalid_input_예외를_던진다() {
        assertThatThrownBy(() -> param(null, null, LATE, EARLY))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 한쪽만_주면_열린_구간으로_본다() {
        assertThatCode(() -> param(EARLY, null, null, LATE)).doesNotThrowAnyException();
    }

    @Test
    void 지역_문자열은_목록_조회와_같은_규칙으로_변환한다() {
        assertThat(param(null, null, null, null).getRegion()).isNull();
        assertThat(SaleOpeningSoonSearchParam
                .of(null, null, " SEOUL ", null, null, null, null, null)
                .getRegion()).isEqualTo(Region.SEOUL);
    }

    private SaleOpeningSoonSearchParam param(
            final LocalDateTime saleStartFrom,
            final LocalDateTime saleStartTo,
            final LocalDateTime saleEndFrom,
            final LocalDateTime saleEndTo
    ) {
        return SaleOpeningSoonSearchParam.of(
                "CONCERT",
                null,
                null,
                saleStartFrom,
                saleStartTo,
                saleEndFrom,
                saleEndTo,
                null
        );
    }
}
