package com.ticket.member.internal.infrastructure.auth.oauth2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KakaoUnlinkHttpClientTest {

    @Mock
    private KakaoUnlinkApiClient kakaoUnlinkApiClient;

    @InjectMocks
    private KakaoUnlinkHttpClient kakaoUnlinkHttpClient;

    @Test
    @SuppressWarnings("unchecked")
    void delegates_unlink_request_with_kakao_form_data() {
        kakaoUnlinkHttpClient.unlink("KakaoAK admin-key", "123");

        ArgumentCaptor<MultiValueMap<String, String>> formCaptor = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(kakaoUnlinkApiClient).unlink(eq("KakaoAK admin-key"), formCaptor.capture());
        assertThat(formCaptor.getValue().getFirst("target_id_type")).isEqualTo("user_id");
        assertThat(formCaptor.getValue().getFirst("target_id")).isEqualTo("123");
    }
}
