package com.ticket.security.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.api.SocialProvider;
import com.ticket.shared.exception.InternalErrorException;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ProviderSocialAccountUnlinker implements SocialAccountUnlinker {
    private static final String KAKAO_ADMIN_AUTH_PREFIX = "KakaoAK ";
    private static final String KAKAO_TARGET_ID_TYPE = "user_id";
    private final KakaoUnlinkApiClient kakaoUnlinkApiClient;
    private final String adminKey;

    public ProviderSocialAccountUnlinker(
            final KakaoUnlinkApiClient kakaoUnlinkApiClient,
            @Value("${app.auth.kakao.admin-key:}") final String adminKey) {
        this.kakaoUnlinkApiClient = kakaoUnlinkApiClient;
        this.adminKey = adminKey;
    }

    @Override
    public void unlink(final SocialAccountSnapshot connection) {
        if (connection.provider() == SocialProvider.KAKAO) {
            unlinkKakao(connection.providerId());
        }
    }

    private void unlinkKakao(final String kakaoUserId) {
        if (!StringUtils.hasText(kakaoUserId)) {
            throw new InvalidRequestException("카카오 사용자 ID가 비어 있습니다.");
        }

        if (!StringUtils.hasText(adminKey)) {
            throw new InvalidRequestException("KAKAO_ADMIN_KEY 설정이 필요합니다.");
        }

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("target_id_type", KAKAO_TARGET_ID_TYPE);
        formData.add("target_id", kakaoUserId);

        try {
            kakaoUnlinkApiClient.unlink(KAKAO_ADMIN_AUTH_PREFIX + adminKey, formData);
        } catch (Exception e) {
            log.error("카카오 unlink 호출 실패", e);
            throw new InternalErrorException("카카오 unlink 호출에 실패했습니다.");
        }
    }
}
