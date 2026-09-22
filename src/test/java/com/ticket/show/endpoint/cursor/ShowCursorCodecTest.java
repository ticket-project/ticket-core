package com.ticket.show.endpoint.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.usecase.ShowCursor;
import com.ticket.show.usecase.ShowSort;

import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings("NonAsciiCharacters")
class ShowCursorCodecTest {

    private final ShowCursorCodec codec =
            new ShowCursorCodec(JsonMapper.builder().build());

    @Test
    void 커서_wire_포맷은_url_safe_base64_json이다() {
        ShowCursor position = new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

        String encoded = codec.encode(position);

        String json = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("{\"sort\":\"POPULAR\",\"dir\":\"DESC\",\"lastValue\":\"10\",\"lastId\":1}");
        assertThat(encoded).doesNotContain("=");
    }

    @Test
    void 최신순_커서에는_마감여부와_판정_시각이_함께_담긴다() {
        ShowCursor position = new ShowCursor(ShowSort.LATEST, "DESC", "2026-09-14T09:00", 7L, 0, "2026-09-14T10:00");

        String json = new String(Base64.getUrlDecoder().decode(codec.encode(position)), StandardCharsets.UTF_8);

        assertThat(json)
                .isEqualTo("{\"sort\":\"LATEST\",\"dir\":\"DESC\",\"lastValue\":\"2026-09-14T09:00\","
                        + "\"lastId\":7,\"saleClosedRank\":0,\"evaluatedAt\":\"2026-09-14T10:00\"}");
    }

    @Test
    void 최신순_커서도_그대로_되읽는다() {
        ShowCursor position = new ShowCursor(ShowSort.LATEST, "DESC", "2026-09-14T09:00", 7L, 1, "2026-09-14T10:00");

        assertThat(codec.decode(codec.encode(position))).isEqualTo(position);
    }

    @Test
    void 인코딩한_커서를_그대로_되읽는다() {
        ShowCursor position = new ShowCursor(ShowSort.SALE_START_APPROACHING, "ASC", "2026-03-15T10:00", 42L);

        assertThat(codec.decode(codec.encode(position))).isEqualTo(position);
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
    @ValueSource(strings = {"cursor-1", "!!!not-base64!!!", "eyJicm9rZW4iOg"})
    void 해석할_수_없는_커서는_400으로_끊는다(final String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor)).isInstanceOf(InvalidRequestException.class);
    }
}
