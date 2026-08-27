package com.ticket.core.app.support.cursor;

import java.util.List;

/**
 * 커서 페이징 조회 결과다.
 *
 * <p>HTTP 커서 문자열은 여기에 담지 않는다. {@code nextPosition}은 다음 페이지의 시작 위치를
 * 나타내는 타입 값이고, 이를 wire 문자열로 바꾸는 일은 core-api가 한다.
 *
 * @param <T> 조회 결과 항목
 * @param <P> 다음 페이지 위치를 나타내는 타입
 */
public record CursorPage<T, P>(
        List<T> items,
        boolean hasNext,
        P nextPosition
) {

    public static <T, P> CursorPage<T, P> empty() {
        return new CursorPage<>(List.of(), false, null);
    }
}
