package com.ticket.catalog.internal.infrastructure.show.query;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.catalog.internal.application.show.query.ShowSort;
import com.ticket.catalog.internal.application.show.query.model.ShowCursor;
import com.ticket.catalog.internal.domain.show.QShow;
import com.ticket.core.support.exception.CoreException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowCursorConditionBuilderTest {

    private final QuerydslShowCursorConditionBuilder showCursorPolicy = new QuerydslShowCursorConditionBuilder();

    @Test
    void cursor가_없으면_where절을_건드리지_않는다() {
        BooleanBuilder where = new BooleanBuilder();

        showCursorPolicy.applyCursor(where, null, popularDesc());

        assertThat(where.hasValue()).isFalse();
    }

    @Test
    void cursor의_sort가_요청과_다르면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.LATEST, "DESC", "2026-03-15T10:00:00", 1L);

        assertThatThrownBy(() -> showCursorPolicy.applyCursor(new BooleanBuilder(), cursor, popularDesc()))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void cursor의_dir가_요청과_다르면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "ASC", "10", 1L);

        assertThatThrownBy(() -> showCursorPolicy.applyCursor(new BooleanBuilder(), cursor, popularDesc()))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void cursor의_lastId가_없으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "DESC", "10", null);

        assertThatThrownBy(() -> showCursorPolicy.applyCursor(new BooleanBuilder(), cursor, popularDesc()))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void cursor의_lastValue가_없으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "DESC", " ", 1L);

        assertThatThrownBy(() -> showCursorPolicy.applyCursor(new BooleanBuilder(), cursor, popularDesc()))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void cursor의_lastValue가_정렬형식과_맞지_않으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.LATEST, "DESC", "not-a-date", 1L);

        assertThatThrownBy(() -> showCursorPolicy.applyCursor(new BooleanBuilder(), cursor, latestDesc()))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void 올바른_cursor면_where절에_조건을_추가한다() {
        BooleanBuilder where = new BooleanBuilder();
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

        showCursorPolicy.applyCursor(where, cursor, popularDesc());

        assertThat(where.hasValue()).isTrue();
    }

    @Test
    void 마지막_행에서_다음_커서_위치를_만든다() {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get(QShow.show.id)).thenReturn(1L);
        when(tuple.get(QShow.show.viewCount)).thenReturn(10L);

        ShowCursor nextPosition = showCursorPolicy.buildNextPosition(List.of(tuple), 1, popularDesc());

        assertThat(nextPosition).isEqualTo(new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L));
    }

    private QuerydslShowSortResolver.SortOrder popularDesc() {
        return new QuerydslShowSortResolver.SortOrder(ShowSort.POPULAR, Sort.Direction.DESC);
    }

    private QuerydslShowSortResolver.SortOrder latestDesc() {
        return new QuerydslShowSortResolver.SortOrder(ShowSort.LATEST, Sort.Direction.DESC);
    }
}
