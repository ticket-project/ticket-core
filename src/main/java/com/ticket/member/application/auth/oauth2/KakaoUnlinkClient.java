package com.ticket.member.application.auth.oauth2;

public interface KakaoUnlinkClient {

    void unlink(String adminAuthorization, String kakaoUserId);
}
