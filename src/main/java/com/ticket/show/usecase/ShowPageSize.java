package com.ticket.show.usecase;

import com.ticket.shared.exception.InvalidRequestException;

/**
 * show 목록 use case가 함께 쓰는 한 번에 조회할 개수 규칙이다. 상한은 use case 조건이므로 API가 아니라 여기가 소유한다.
 *
 * <p>상한이 없으면 {@code size=1000000} 같은 요청 하나가 그대로 {@code LIMIT}이 되어 DB와 메모리를 붙잡는다. 목록마다 다르게 둘 이유가 없어 한 값만 둔다.
 */
public final class ShowPageSize {
    public static final int MAX = 100;

    private ShowPageSize() {}

    /** @throws InvalidRequestException {@code size}가 1 이상 {@link #MAX} 이하가 아닐 때 */
    public static void require(final int size) {
        if (size <= 0 || size > MAX) {
            throw new InvalidRequestException("size는 1 이상 " + MAX + " 이하여야 합니다.");
        }
    }
}
