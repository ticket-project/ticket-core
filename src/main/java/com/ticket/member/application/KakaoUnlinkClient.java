package com.ticket.member.application;

public interface KakaoUnlinkClient {

    void unlink(String adminAuthorization, String kakaoUserId);
}
