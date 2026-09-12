package com.ticket.member.account.application;

/** 회원 탈퇴 후 외부 provider의 소셜 계정 연결을 해제한다. */
public interface SocialAccountUnlinker {

    void unlink(SocialAccountConnection connection);
}
