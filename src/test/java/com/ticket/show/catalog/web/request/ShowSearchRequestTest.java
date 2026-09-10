package com.ticket.show.catalog.web.request;

import com.ticket.show.catalog.web.cursor.ShowCursorCodec;
import com.ticket.show.catalog.application.ShowCursor;
import com.ticket.show.catalog.application.ShowSearchCriteria;
import com.ticket.show.catalog.application.ShowSort;
import com.ticket.show.catalog.domain.SaleDisplayStatus;
import com.ticket.venue.Region;
import com.ticket.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowSearchRequestTest {

    private static final ShowCursorCodec CURSOR_CODEC =
            new ShowCursorCodec(JsonMapper.builder().build());

    private static final ShowCursor CURSOR_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

    private static final String ENCODED_CURSOR = CURSOR_CODEC.encode(CURSOR_POSITION);

    @Test
    void 요청값을_domain_검색조건으로_변환한다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬",
                "MUSICAL",
                "ON_SALE",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "SEOUL",
                ENCODED_CURSOR
        );

        ShowSearchCriteria criteria = request.toCriteria(CURSOR_CODEC);

        assertThat(criteria.getKeyword()).isEqualTo("뮤지컬");
        assertThat(criteria.getCategory()).isEqualTo("MUSICAL");
        assertThat(criteria.getSaleDisplayStatus()).isEqualTo(SaleDisplayStatus.ON_SALE);
        assertThat(criteria.getStartDateFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(criteria.getStartDateTo()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(criteria.getRegion()).isEqualTo(Region.SEOUL);
        assertThat(criteria.getCursor()).isEqualTo(CURSOR_POSITION);
    }

    @Test
    void cursor가_없으면_첫_페이지로_조회한다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬", "MUSICAL", "ON_SALE", null, null, "SEOUL", null
        );

        assertThat(request.toCriteria(CURSOR_CODEC).getCursor()).isNull();
    }

    @Test
    void 해석할_수_없는_cursor_문자열이면_invalid_request_예외를_던진다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬", "MUSICAL", "ON_SALE", null, null, "SEOUL", "cursor-1"
        );

        assertThatThrownBy(() -> request.toCriteria(CURSOR_CODEC))
                .isInstanceOf(InvalidRequestException.class);
    }

    /**
     * 건수 조회는 커서를 쓰지 않는다. 잘못된 커서가 붙어도 집계는 실패하지 않아야 한다.
     */
    @Test
    void 건수_조회는_커서를_해석하지_않는다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬", "MUSICAL", "ON_SALE", null, null, "SEOUL", "garbage"
        );

        ShowSearchCriteria criteria = request.toCountCriteria();

        assertThat(criteria.getCursor()).isNull();
        assertThat(criteria.getKeyword()).isEqualTo("뮤지컬");
    }

    @Test
    void 알_수_없는_region_문자열이면_invalid_request_예외를_던진다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬",
                "MUSICAL",
                "ON_SALE",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "NOWHERE",
                ENCODED_CURSOR
        );

        assertThatThrownBy(() -> request.toCriteria(CURSOR_CODEC))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 시작일_From이_To보다_늦으면_예외를_던진다() {
        ShowSearchRequest request = new ShowSearchRequest(
                "뮤지컬",
                "MUSICAL",
                "ON_SALE",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1),
                "SEOUL",
                null
        );

        assertThatThrownBy(() -> request.toCriteria(CURSOR_CODEC))
                .isInstanceOf(InvalidRequestException.class);
    }
}
