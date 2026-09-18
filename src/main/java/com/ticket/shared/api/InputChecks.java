package com.ticket.shared.api;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.InvalidRequestException;

/**
 * use case {@code Input}이 반복해 쓰던 필수·양수 판정 두 개다. 판정 결과인 {@link InvalidRequestException}의 상세 문구는 그대로
 * {@code error.data}로 나가는 공개 계약이라, 문구와 null·0 이하의 구분을 여기 한 벌만 둔다.
 *
 * <p>검증 framework가 아니다 — 여러 Input이 <b>문구까지 똑같이</b> 반복하던 판정만 담는다. 조회별 size 상한처럼 Input마다 다른 규칙, 좌석
 * 중복·판매 가능 시간처럼 도메인이 소유한 규칙은 각자의 자리에 남는다.
 */
public final class InputChecks {
    private InputChecks() {}

    /** 값이 없으면 {@code "<필드명>는 필수입니다."}, 0 이하면 {@code "<필드명>는 양수여야 합니다."}로 알린다. */
    public static long requirePositiveId(final @Nullable Long value, final String fieldName) {
        final long id = requireProvided(value, fieldName);
        if (id <= 0) {
            throw new InvalidRequestException(fieldName + "는 양수여야 합니다.");
        }
        return id;
    }

    /** 값이 없으면 {@code "<필드명>는 필수입니다."}로 알린다. */
    public static <T> T requireProvided(final @Nullable T value, final String fieldName) {
        if (value == null) {
            throw new InvalidRequestException(fieldName + "는 필수입니다.");
        }
        return value;
    }
}
