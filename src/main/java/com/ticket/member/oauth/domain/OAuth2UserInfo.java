package com.ticket.member.oauth.domain;

import com.ticket.member.account.domain.SocialProvider;

public interface OAuth2UserInfo {
    SocialProvider provider();

    String providerId();

    String email();

    String name();
}
