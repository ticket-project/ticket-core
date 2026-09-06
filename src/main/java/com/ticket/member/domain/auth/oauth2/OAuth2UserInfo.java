package com.ticket.member.domain.auth.oauth2;

import com.ticket.member.domain.member.model.SocialProvider;

public interface OAuth2UserInfo {
    SocialProvider provider();

    String providerId();

    String email();

    String name();
}
