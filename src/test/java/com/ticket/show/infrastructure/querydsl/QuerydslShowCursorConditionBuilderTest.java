package com.ticket.show.infrastructure.querydsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.application.query.ShowCursor;
import com.ticket.show.application.query.ShowSort;
import com.ticket.show.domain.show.QShow;

@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowCursorConditionBuilderTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0);
    private final QuerydslShowSortResolver sortResolver =
            new QuerydslShowSortResolver(
                    new SaleDisplayStatusPredicates(),
                    Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.from(ZoneOffset.UTC)));
    private final QuerydslShowCursorConditionBuilder showCursorPolicy =
            new QuerydslShowCursorConditionBuilder(sortResolver);

    @Test
    void cursor가_없으면_where절을_건드리지_않는다() {
        BooleanBuilder where = new BooleanBuilder();

        showCursorPolicy.applyCursor(where, null, popularDesc());

        assertThat(where.hasValue()).isFalse();
    }

    @Test
    void cursor의_sort가_요청과_다르면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.LATEST, "DESC", "2026-03-15T10:00:00", 1L);

        assertThatThrownBy(
                        () ->
                                showCursorPolicy.applyCursor(
                                        new BooleanBuilder(), cursor, popularDesc()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cursor의_dir가_요청과_다르면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "ASC", "10", 1L);

        assertThatThrownBy(
                        () ->
                                showCursorPolicy.applyCursor(
                                        new BooleanBuilder(), cursor, popularDesc()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cursor의_lastId가_없으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "DESC", "10", null);

        assertThatThrownBy(
                        () ->
                                showCursorPolicy.applyCursor(
                                        new BooleanBuilder(), cursor, popularDesc()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cursor의_lastValue가_없으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "DESC", " ", 1L);

        assertThatThrownBy(
                        () ->
                                showCursorPolicy.applyCursor(
                                        new BooleanBuilder(), cursor, popularDesc()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cursor의_lastValue가_정렬형식과_맞지_않으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor =
                new ShowCursor(ShowSort.LATEST, "DESC", "not-a-date", 1L, 0, NOW.toString());

        assertThatThrownBy(
                        () ->
                                showCursorPolicy.applyCursor(
                                        new BooleanBuilder(), cursor, latestDesc()))
                .isInstanceOf(InvalidRequestException.class);
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

        ShowCursor nextPosition =
                showCursorPolicy.buildNextPosition(List.of(tuple), 1, popularDesc());

        assertThat(nextPosition).isEqualTo(new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L));
    }

    @Test
    void 최신순_커서에_마감여부가_없으면_INVALID_INPUT_예외를_던진다() {
        // 최신순이 마감 여부를 먼저 보기 전에 발급된 커서다. 그대로 이어 읽으면 마감 그룹 경계를
        // 무시하고 등록일만으로 잘라 중복·누락이 생긴다.
        ShowCursor legacyCursor = new ShowCursor(ShowSort.LATEST, "DESC", "2026-09-14T09:00", 1L);

        assertThatThrownBy(
                        () ->
                                showCursorPolicy.applyCursor(
                                        new BooleanBuilder(), legacyCursor, latestDesc()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 최신순_커서는_마감여부와_등록일과_id를_모두_조건에_건다() {
        BooleanBuilder where = new BooleanBuilder();
        ShowCursor cursor =
                new ShowCursor(ShowSort.LATEST, "DESC", "2026-09-14T09:00", 1L, 0, NOW.toString());

        showCursorPolicy.applyCursor(where, cursor, latestDesc());

        assertThat(where.hasValue()).isTrue();
        String condition = where.getValue().toString();
        assertThat(condition).as("마감 여부 그룹 경계").contains("case");
        assertThat(condition).as("등록일 경계").contains("createdAt");
        assertThat(condition).as("동점 id 경계").contains("id");
    }

    @Test
    void 최신순_다음_커서에_마감여부와_판정_시각을_담는다() {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get(QShow.show.id)).thenReturn(7L);
        when(tuple.get(QShow.show.createdAt)).thenReturn(LocalDateTime.of(2026, 9, 14, 9, 0));
        when(tuple.get(QShow.show.displaySaleWindow.startsAt))
                .thenReturn(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(tuple.get(QShow.show.displaySaleWindow.endsAt))
                .thenReturn(LocalDateTime.of(2026, 10, 1, 10, 0));

        ShowCursor nextPosition =
                showCursorPolicy.buildNextPosition(List.of(tuple), 1, latestDesc());

        assertThat(nextPosition)
                .isEqualTo(
                        new ShowCursor(
                                ShowSort.LATEST,
                                "DESC",
                                "2026-09-14T09:00",
                                7L,
                                0,
                                NOW.toString()));
    }

    @Test
    void 판매기간이_끝난_공연은_다음_커서에서_마감으로_적힌다() {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get(QShow.show.id)).thenReturn(7L);
        when(tuple.get(QShow.show.createdAt)).thenReturn(LocalDateTime.of(2026, 9, 14, 9, 0));
        when(tuple.get(QShow.show.displaySaleWindow.startsAt))
                .thenReturn(LocalDateTime.of(2026, 8, 1, 10, 0));
        when(tuple.get(QShow.show.displaySaleWindow.endsAt)).thenReturn(NOW.minusSeconds(1));

        ShowCursor nextPosition =
                showCursorPolicy.buildNextPosition(List.of(tuple), 1, latestDesc());

        assertThat(nextPosition.saleClosedRank()).isEqualTo(1);
    }

    @Test
    void 표시_판매기간이_비어있는_공연도_마감으로_본다() {
        // DisplaySaleWindow.statusAt의 규칙과 같다 — null 창은 CLOSED다(TD-12).
        Tuple tuple = mock(Tuple.class);
        when(tuple.get(QShow.show.id)).thenReturn(7L);
        when(tuple.get(QShow.show.createdAt)).thenReturn(LocalDateTime.of(2026, 9, 14, 9, 0));
        when(tuple.get(QShow.show.displaySaleWindow.startsAt)).thenReturn(null);
        when(tuple.get(QShow.show.displaySaleWindow.endsAt)).thenReturn(null);

        ShowCursor nextPosition =
                showCursorPolicy.buildNextPosition(List.of(tuple), 1, latestDesc());

        assertThat(nextPosition.saleClosedRank()).isEqualTo(1);
    }

    private QuerydslShowSortResolver.SortOrder popularDesc() {
        return new QuerydslShowSortResolver.SortOrder(ShowSort.POPULAR, Sort.Direction.DESC, null);
    }

    private QuerydslShowSortResolver.SortOrder latestDesc() {
        return new QuerydslShowSortResolver.SortOrder(ShowSort.LATEST, Sort.Direction.DESC, NOW);
    }
}
