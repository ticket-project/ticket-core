package com.ticket.member.domain;

import com.ticket.member.domain.SocialProvider;

public interface OAuth2UserInfo {
    SocialProvider provider();

    String providerId();

    String email();

    String name();
}
