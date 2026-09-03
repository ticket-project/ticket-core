package com.ticket.shared;

import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class RequiredInputTest {

    @Test
    void 식별자는_null이거나_0_이하면_invalid_input이다() {
        assertInvalidInput(() -> RequiredInput.positiveId(null, "memberId"));
        assertInvalidInput(() -> RequiredInput.positiveId(0L, "memberId"));
        assertInvalidInput(() -> RequiredInput.positiveId(-1L, "memberId"));
        assertThat(RequiredInput.positiveId(1L, "memberId")).isEqualTo(1L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 필수_문자열은_비어있을_수_없다(final String value) {
        assertInvalidInput(() -> RequiredInput.notBlank(value, "orderKey"));
    }

    @Test
    void 페이지_크기는_1_이상이어야_한다() {
        assertInvalidInput(() -> RequiredInput.positiveSize(0, "size"));
        assertThatCode(() -> RequiredInput.positiveSize(1, "size")).doesNotThrowAnyException();
    }

    @Test
    void 상한이_있는_페이지_크기는_범위_안이어야_한다() {
        assertInvalidInput(() -> RequiredInput.sizeWithin(0, 100, "size"));
        assertInvalidInput(() -> RequiredInput.sizeWithin(101, 100, "size"));
        assertThatCode(() -> RequiredInput.sizeWithin(100, 100, "size")).doesNotThrowAnyException();
    }

    /**
     * 공개 메시지는 ErrorType이 소유하고, 어떤 component가 문제인지는 진단용 data에 담긴다.
     */
    @Test
    void 어떤_component가_문제인지_진단_data에_담는다() {
        assertThatThrownBy(() -> RequiredInput.positiveId(null, "performanceId"))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getData())
                        .asString()
                        .contains("performanceId"));
    }

    private void assertInvalidInput(final Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }
}
