package com.ticket.core.app.auth.oauth2;

import com.ticket.core.domain.auth.oauth2.KakaoUnlinkClient;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class KakaoUnlinkService {

    private static final String KAKAO_ADMIN_AUTH_PREFIX = "KakaoAK ";

    private final KakaoUnlinkClient kakaoUnlinkClient;
    private final String adminKey;

    public KakaoUnlinkService(
            final KakaoUnlinkClient kakaoUnlinkClient,
            @Value("${app.auth.kakao.admin-key:}") final String adminKey
    ) {
        this.kakaoUnlinkClient = kakaoUnlinkClient;
        this.adminKey = adminKey;
    }

    public void unlinkByUserId(final String kakaoUserId) {
        if (!StringUtils.hasText(kakaoUserId)) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "카카오 사용자 ID가 비어 있습니다.");
        }

        if (!StringUtils.hasText(adminKey)) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "KAKAO_ADMIN_KEY 설정이 필요합니다.");
        }

        try {
            kakaoUnlinkClient.unlink(KAKAO_ADMIN_AUTH_PREFIX + adminKey, kakaoUserId);
        } catch (Exception e) {
            log.error("카카오 unlink 호출 실패", e);
            throw new CoreException(ErrorType.DEFAULT_ERROR, "카카오 unlink 호출에 실패했습니다.");
        }
    }
}
