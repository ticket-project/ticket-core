package com.ticket.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.api.SocialProvider;
import com.ticket.shared.exception.InternalErrorException;
import com.ticket.shared.exception.InvalidRequestException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ProviderSocialAccountUnlinkerTest {
    @Mock private KakaoUnlinkApiClient kakaoUnlinkApiClient;

    @Test
    void 사용자아이디가_비어있으면_예외를_던진다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("admin-key");

        assertThatThrownBy(() -> unlinker.unlink(kakao(" ")))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(kakaoUnlinkApiClient);
    }

    @Test
    void 관리자키가_비어있으면_예외를_던진다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("");

        assertThatThrownBy(() -> unlinker.unlink(kakao("123")))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(kakaoUnlinkApiClient);
    }

    /** 옛 {@code KakaoUnlinkHttpClientTest}가 고정하던 form data 형식을 이어받는다. */
    @Test
    @SuppressWarnings("unchecked")
    void 정상요청이면_카카오_unlink_API를_카카오_form_형식으로_호출한다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("admin-key");

        unlinker.unlink(kakao("123"));

        final ArgumentCaptor<MultiValueMap<String, String>> formCaptor =
                ArgumentCaptor.forClass(MultiValueMap.class);
        verify(kakaoUnlinkApiClient).unlink(eq("KakaoAK admin-key"), formCaptor.capture());
        assertThat(formCaptor.getValue().getFirst("target_id_type")).isEqualTo("user_id");
        assertThat(formCaptor.getValue().getFirst("target_id")).isEqualTo("123");
    }

    @Test
    void 카카오_API_실패는_기본오류로_변환한다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("admin-key");
        doThrow(new IllegalStateException("boom"))
                .when(kakaoUnlinkApiClient)
                .unlink(anyString(), any());

        assertThatThrownBy(() -> unlinker.unlink(kakao("123")))
                .isInstanceOf(InternalErrorException.class);
    }

    @Test
    void 카카오_외_provider는_외부_API를_호출하지_않는다() {
        final ProviderSocialAccountUnlinker unlinker = unlinker("admin-key");

        unlinker.unlink(new SocialAccountSnapshot(SocialProvider.GOOGLE, "google-123"));

        verifyNoInteractions(kakaoUnlinkApiClient);
    }

    private ProviderSocialAccountUnlinker unlinker(final String adminKey) {
        return new ProviderSocialAccountUnlinker(kakaoUnlinkApiClient, adminKey);
    }

    private SocialAccountSnapshot kakao(final String providerId) {
        return new SocialAccountSnapshot(SocialProvider.KAKAO, providerId);
    }
}
