package com.ticket.like.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.like.domain.LikeRepository;
import com.ticket.like.domain.LikeType;

/**
 * {@code LikeQueryApi}가 String으로 대상 종류를 받으면서 사라진 compile-time 안정성을 여기서 되돌려 받는다 — 허용 값, 대소문자·공백 정책, size 하한을 계약대로 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class LikeQueryServiceTest {
    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeQueryService service;

    @Test
    void 허용된_대상_종류는_show_하나다() {
        when(likeRepository.countByLikeTypeAndTargetId(LikeType.SHOW, 7L)).thenReturn(3L);

        assertThat(service.countByTarget("show", 7L)).isEqualTo(3L);
    }

    /** 소문자 정확 일치다 — 공백을 다듬거나 대소문자를 맞춰주지 않는다. 사용자 입력을 그대로 넘기지 말라는 뜻이다. */
    @ParameterizedTest
    @ValueSource(strings = {"SHOW", "Show", " show", "show ", "venue", ""})
    void 허용되지_않은_대상_종류는_거절한다(final String targetType) {
        assertThatThrownBy(() -> service.countByTarget(targetType, 7L)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(likeRepository);
    }

    /** size 0은 예전에 adapter의 {@code subList(0, 0)} + {@code getLast()}에서 NoSuchElementException으로 터졌다. */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void size가_1_미만이면_계약_위반으로_끊는다(final int size) {
        assertThatThrownBy(() -> service.findLiked("show", 1L, null, size))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");

        verifyNoInteractions(likeRepository);
    }
}
