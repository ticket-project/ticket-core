package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ticket.show.exception.ShowErrorCode;
import com.ticket.show.exception.ShowException;

/**
 * 목록 use case마다 정렬 키가 null이 될 수 없는 정렬만 받는지 고정한다. null 키 행이 페이지 끝에 오면 다음 커서를 만들지 못해 500이 된다.
 *
 * <p>두 use case의 {@code Input} 계약이 같은 규칙({@link ShowSort#requireOneOf})의 목록별 허용 집합이라 한 곳에 모았다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ShowListSortSupportTest {
    private static final ShowListParam LIST_PARAM = new ShowListParam(null, null, null, null);
    private static final ShowSearchCriteria SEARCH_CRITERIA =
            new ShowSearchCriteria(null, null, null, null, null, null, null);

    @Test
    void 전체_목록은_판매_시작_임박순을_받지_않는다() {
        assertThatThrownBy(() -> new GetShowsUseCase.Input(LIST_PARAM, 10, ShowSort.SALE_START_APPROACHING))
                .isInstanceOf(ShowException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShowErrorCode.E7002);
        assertThatCode(() -> new GetShowsUseCase.Input(LIST_PARAM, 10, ShowSort.SHOW_START_APPROACHING))
                .doesNotThrowAnyException();
    }

    @Test
    void 검색은_판매_시작_임박순을_받지_않는다() {
        assertThatThrownBy(() -> new SearchShowsUseCase.Input(SEARCH_CRITERIA, 10, ShowSort.SALE_START_APPROACHING))
                .isInstanceOf(ShowException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShowErrorCode.E7002);
        assertThatCode(() -> new SearchShowsUseCase.Input(SEARCH_CRITERIA, 10, ShowSort.SHOW_START_APPROACHING))
                .doesNotThrowAnyException();
    }
}
