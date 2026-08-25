package com.ticket.core.app.auth.oauth2;

import com.ticket.core.domain.auth.oauth2.KakaoUnlinkClient;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class KakaoUnlinkServiceTest {

    @Mock
    private KakaoUnlinkClient kakaoUnlinkClient;

    @Test
    void 사용자아이디가_비어있으면_예외를_던진다() {
        //given
        KakaoUnlinkService kakaoUnlinkService = new KakaoUnlinkService(kakaoUnlinkClient, "admin-key");

        //when
        //then
        assertThatThrownBy(() -> kakaoUnlinkService.unlinkByUserId(" "))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));

        verifyNoInteractions(kakaoUnlinkClient);
    }

    @Test
    void 관리자키가_비어있으면_예외를_던진다() {
        //given
        KakaoUnlinkService kakaoUnlinkService = new KakaoUnlinkService(kakaoUnlinkClient, "");

        //when
        //then
        assertThatThrownBy(() -> kakaoUnlinkService.unlinkByUserId("123"))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));

        verifyNoInteractions(kakaoUnlinkClient);
    }

    @Test
    void 정상요청이면_카카오_unlink_API를_호출한다() {
        //given
        KakaoUnlinkService kakaoUnlinkService = new KakaoUnlinkService(kakaoUnlinkClient, "admin-key");

        kakaoUnlinkService.unlinkByUserId("123");

        //when
        //then
        verify(kakaoUnlinkClient).unlink("KakaoAK admin-key", "123");
    }

    @Test
    void 카카오_API_실패는_기본오류로_변환한다() {
        //given
        KakaoUnlinkService kakaoUnlinkService = new KakaoUnlinkService(kakaoUnlinkClient, "admin-key");
        doThrow(new IllegalStateException("boom")).when(kakaoUnlinkClient).unlink(anyString(), anyString());

        //when
        //then
        assertThatThrownBy(() -> kakaoUnlinkService.unlinkByUserId("123"))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.DEFAULT_ERROR));
    }
}

