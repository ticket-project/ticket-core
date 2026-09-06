package com.ticket.member.internal.application.auth.oauth2;

public interface KakaoUnlinkClient {

    void unlink(String adminAuthorization, String kakaoUserId);
}
