package com.ticket.member.oauth.infrastructure;

import com.ticket.member.account.application.SocialAccountConnection;
import com.ticket.member.account.application.SocialAccountUnlinker;
import com.ticket.member.account.domain.SocialProvider;
import com.ticket.shared.exception.InternalErrorException;
import com.ticket.shared.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class ProviderSocialAccountUnlinker implements SocialAccountUnlinker {

    private static final String KAKAO_ADMIN_AUTH_PREFIX = "KakaoAK ";

    private final KakaoUnlinkHttpClient kakaoUnlinkHttpClient;
    private final String adminKey;

    public ProviderSocialAccountUnlinker(
            final KakaoUnlinkHttpClient kakaoUnlinkHttpClient,
            @Value("${app.auth.kakao.admin-key:}") final String adminKey
    ) {
        this.kakaoUnlinkHttpClient = kakaoUnlinkHttpClient;
        this.adminKey = adminKey;
    }

    @Override
    public void unlink(final SocialAccountConnection connection) {
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

        try {
            kakaoUnlinkHttpClient.unlink(KAKAO_ADMIN_AUTH_PREFIX + adminKey, kakaoUserId);
        } catch (Exception e) {
            log.error("카카오 unlink 호출 실패", e);
            throw new InternalErrorException("카카오 unlink 호출에 실패했습니다.");
        }
    }
}
