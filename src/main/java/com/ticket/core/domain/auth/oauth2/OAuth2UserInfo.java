package com.ticket.core.domain.auth.oauth2;

import com.ticket.core.domain.member.model.SocialProvider;

public interface OAuth2UserInfo {
    SocialProvider provider();

    String providerId();

    String email();

    String name();
}
