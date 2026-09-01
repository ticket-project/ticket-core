package com.ticket.core.infra.hold;

import com.ticket.core.domain.hold.model.Hold;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class HoldMetaCodecTest {

    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private HoldMetaCodec codec;

    @Test
    void hold를_json으로_인코딩한다() {
        Hold hold = createHold();
        when(jsonMapper.writeValueAsString(hold)).thenReturn("{\"holdKey\":\"hold-key\"}");

        String result = codec.encode(hold);

        assertThat(result).isEqualTo("{\"holdKey\":\"hold-key\"}");
    }

    @Test
    void 인코딩에_실패하면_IllegalStateException을_던진다() {
        Hold hold = createHold();
        when(jsonMapper.writeValueAsString(hold)).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> codec.encode(hold))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encode failed");
    }

    @Test
    void payload를_hold로_디코딩한다() {
        Hold hold = createHold();
        when(jsonMapper.readValue("{\"holdKey\":\"hold-key\"}", Hold.class)).thenReturn(hold);

        Hold result = codec.decode("{\"holdKey\":\"hold-key\"}");

        assertThat(result).isEqualTo(hold);
    }

    @Test
    void 디코딩에_실패하면_IllegalStateException을_던진다() {
        when(jsonMapper.readValue("broken", Hold.class)).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> codec.decode("broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decode failed");
    }

    /**
     * Redis에 저장되는 메타 형식이 바뀌면 기존 key를 읽지 못하므로 필드 구성을 고정한다.
     */
    @Test
    void 저장_형식은_hold의_다섯_필드를_선언_순서대로_담는다() {
        HoldMetaCodec realCodec = new HoldMetaCodec(JsonMapper.builder().build());

        String json = realCodec.encode(createHold());

        assertThat(json).isEqualTo(
                "{\"holdKey\":\"hold-key\",\"memberId\":1,\"performanceId\":10,"
                        + "\"seatIds\":[100,101],\"expiresAt\":\"2026-03-15T12:30:00\"}");
    }

    private Hold createHold() {
        return new Hold(
                "hold-key",
                1L,
                10L,
                List.of(100L, 101L),
                LocalDateTime.of(2026, 3, 15, 12, 30)
        );
    }
}
