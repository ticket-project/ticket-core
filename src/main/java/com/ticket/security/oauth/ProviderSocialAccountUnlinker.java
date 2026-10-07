package com.ticket.security.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.api.SocialProvider;

import lombok.extern.slf4j.Slf4j;

/** 연결 해제 실패는 탈퇴를 막지 않는다. 호출자가 결과를 쓰지 않으므로 실패는 여기서 한 번 로그로 남기고 끝낸다. */
@Slf4j
@Component
public class ProviderSocialAccountUnlinker {
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

    public void unlink(final SocialAccountSnapshot connection) {
        if (connection.provider() == SocialProvider.KAKAO) {
            unlinkKakao(connection.providerId());
        }
    }

    private void unlinkKakao(final String kakaoUserId) {
        if (!StringUtils.hasText(kakaoUserId)) {
            log.warn("카카오 unlink를 건너뜁니다. 카카오 사용자 ID가 비어 있습니다.");
            return;
        }

        if (!StringUtils.hasText(adminKey)) {
            log.warn("카카오 unlink를 건너뜁니다. KAKAO_ADMIN_KEY 설정이 필요합니다.");
            return;
        }

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("target_id_type", KAKAO_TARGET_ID_TYPE);
        formData.add("target_id", kakaoUserId);

        try {
            kakaoUnlinkApiClient.unlink(KAKAO_ADMIN_AUTH_PREFIX + adminKey, formData);
        } catch (Exception e) {
            log.warn("카카오 unlink 호출에 실패했습니다.", e);
        }
    }
}
