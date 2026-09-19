package com.ticket.show.usecase;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 커서 페이지네이션의 위치다. {@code lastValue}/{@code lastId}는 정렬 기준값과 동점 처리용 id다.
 *
 * <p>{@link ShowSort#LATEST}는 정렬 키가 셋이라({@code 마감 여부 -> 등록일 -> id}) 두 필드를 더 쓴다.
 *
 * <ul>
 *   <li>{@code saleClosedRank} — 마지막 행의 마감 여부(0=미마감, 1=마감).
 *   <li>{@code evaluatedAt} — 그 마감 여부를 판정한 시각. 첫 페이지에서 고정해 이후 페이지가 그대로 쓴다. 페이지마다 현재 시각으로 다시 판정하면,
 *       페이지를 넘기는 사이 마감된 공연이 뒷 그룹으로 옮겨가 건너뛰거나 중복된다.
 * </ul>
 *
 * <p>나머지 정렬에서 이 두 값은 {@code null}이고, 커서 문자열에도 담기지 않는다 — 최신순 외의 정렬은 이 변경 전에 발급된 커서 문자열과 형식이 그대로 같다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShowCursor(
        ShowSort sort,
        String dir,
        String lastValue,
        Long lastId,
        @Nullable Integer saleClosedRank,
        @Nullable String evaluatedAt) {
    public ShowCursor(
            final ShowSort sort, final String dir, final String lastValue, final Long lastId) {
        this(sort, dir, lastValue, lastId, null, null);
    }
}
