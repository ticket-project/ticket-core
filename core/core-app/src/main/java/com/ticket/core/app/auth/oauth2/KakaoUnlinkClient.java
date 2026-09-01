package com.ticket.core.app.auth.oauth2;

public interface KakaoUnlinkClient {

    void unlink(String adminAuthorization, String kakaoUserId);
}
