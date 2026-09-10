package com.ticket.show.catalog.web.cursor;

import com.ticket.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowLikeCursorCodecTest {

    private final ShowLikeCursorCodec codec = new ShowLikeCursorCodec();

    @Test
    void 마지막_찜_id를_십진수_문자열로_그대로_쓴다() {
        assertThat(codec.encode(9L)).isEqualTo("9");
        assertThat(codec.decode("9")).isEqualTo(9L);
    }

    @Test
    void 다음_페이지가_없으면_커서를_만들지_않는다() {
        assertThat(codec.encode(null)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 커서가_비어있으면_첫_페이지로_본다(final String cursor) {
        assertThat(codec.decode(cursor)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9999999999999999999999"})
    void 숫자가_아니면_400으로_끊는다(final String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(InvalidRequestException.class);
    }
}
