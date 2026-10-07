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
    void 대상의_찜_개수를_센다() {
        when(likeRepository.countByTargetId(7L)).thenReturn(3L);

        assertThat(service.countByTarget(7L)).isEqualTo(3L);
    }

    /** size 0은 예전에 adapter의 {@code subList(0, 0)} + {@code getLast()}에서 NoSuchElementException으로 터졌다. */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void size가_1_미만이면_계약_위반으로_끊는다(final int size) {
        assertThatThrownBy(() -> service.findLiked(1L, null, size))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");

        verifyNoInteractions(likeRepository);
    }
}
