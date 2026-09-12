package com.ticket.member.oauth.infrastructure;

import com.ticket.member.account.application.SocialAccountConnection;
import com.ticket.member.account.domain.SocialProvider;
import com.ticket.shared.exception.InternalErrorException;
import com.ticket.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ProviderSocialAccountUnlinkerTest {

    @Mock
    private KakaoUnlinkHttpClient kakaoUnlinkHttpClient;

    @Test
    void 사용자아이디가_비어있으면_예외를_던진다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("admin-key");

        assertThatThrownBy(() -> unlinker.unlink(kakao(" ")))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(kakaoUnlinkHttpClient);
    }

    @Test
    void 관리자키가_비어있으면_예외를_던진다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("");

        assertThatThrownBy(() -> unlinker.unlink(kakao("123")))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(kakaoUnlinkHttpClient);
    }

    @Test
    void 정상요청이면_카카오_unlink_API를_호출한다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("admin-key");

        unlinker.unlink(kakao("123"));

        verify(kakaoUnlinkHttpClient).unlink("KakaoAK admin-key", "123");
    }

    @Test
    void 카카오_API_실패는_기본오류로_변환한다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("admin-key");
        doThrow(new IllegalStateException("boom"))
                .when(kakaoUnlinkHttpClient)
                .unlink(anyString(), anyString());

        assertThatThrownBy(() -> unlinker.unlink(kakao("123")))
                .isInstanceOf(InternalErrorException.class);
    }

    @Test
    void 카카오_외_provider는_외부_API를_호출하지_않는다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("admin-key");

        unlinker.unlink(new SocialAccountConnection(SocialProvider.GOOGLE, "google-123"));

        verifyNoInteractions(kakaoUnlinkHttpClient);
    }

    private ProviderSocialAccountUnlinker unlinker(final String adminKey) {
        return new ProviderSocialAccountUnlinker(kakaoUnlinkHttpClient, adminKey);
    }

    private SocialAccountConnection kakao(final String providerId) {
        return new SocialAccountConnection(SocialProvider.KAKAO, providerId);
    }
}
