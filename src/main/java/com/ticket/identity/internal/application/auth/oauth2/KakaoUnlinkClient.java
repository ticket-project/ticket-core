package com.ticket.identity.internal.application.auth.oauth2;

public interface KakaoUnlinkClient {

    void unlink(String adminAuthorization, String kakaoUserId);
}
