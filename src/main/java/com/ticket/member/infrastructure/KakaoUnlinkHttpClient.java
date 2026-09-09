package com.ticket.member.infrastructure;

import com.ticket.member.application.KakaoUnlinkClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
@RequiredArgsConstructor
public class KakaoUnlinkHttpClient implements KakaoUnlinkClient {

    private static final String TARGET_ID_TYPE = "user_id";

    private final KakaoUnlinkApiClient kakaoUnlinkApiClient;

    @Override
    public void unlink(final String adminAuthorization, final String kakaoUserId) {
        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("target_id_type", TARGET_ID_TYPE);
        formData.add("target_id", kakaoUserId);

        kakaoUnlinkApiClient.unlink(adminAuthorization, formData);
    }
}
