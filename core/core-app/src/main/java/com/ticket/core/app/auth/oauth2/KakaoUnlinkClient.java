package com.ticket.core.domain.auth.oauth2;

public interface KakaoUnlinkClient {

    void unlink(String adminAuthorization, String kakaoUserId);
}
