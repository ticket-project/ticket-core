package com.ticket.shared.api;

import static com.ticket.shared.api.InputChecks.requirePositiveId;
import static com.ticket.shared.api.InputChecks.requireProvided;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ticket.shared.exception.InvalidRequestException;

/**
 * 오류 상세 문구는 {@code error.data}로 그대로 공개되는 계약이라 문자열까지 고정한다. null과 0 이하를 구분하는 것도 계약이다 — 둘을 같은 오류로 뭉개면 클라이언트가 보던 사유가 바뀐다.
 */
@SuppressWarnings("NonAsciiCharacters")
class InputChecksTest {
    @Test
    void 양수_id는_그대로_통과한다() {
        assertThat(requirePositiveId(7L, "memberId")).isEqualTo(7L);
    }

    @Test
    void id가_null이면_필수_오류다() {
        assertThatThrownBy(() -> requirePositiveId(null, "memberId"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getData())
                .isEqualTo("memberId는 필수입니다.");
    }

    @Test
    void id가_0이면_양수_오류다() {
        assertThatThrownBy(() -> requirePositiveId(0L, "performanceId"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getData())
                .isEqualTo("performanceId는 양수여야 합니다.");
    }

    @Test
    void id가_음수면_양수_오류다() {
        assertThatThrownBy(() -> requirePositiveId(-1L, "performanceId"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getData())
                .isEqualTo("performanceId는 양수여야 합니다.");
    }

    @Test
    void 값이_있으면_그대로_돌려준다() {
        assertThat(requireProvided("ASC", "sort")).isEqualTo("ASC");
    }

    @Test
    void 값이_없으면_필수_오류다() {
        assertThatThrownBy(() -> requireProvided(null, "sort"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getData())
                .isEqualTo("sort는 필수입니다.");
    }
}
