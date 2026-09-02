package com.ticket.identity.internal.domain.auth.oauth2;

import com.ticket.identity.internal.domain.member.model.SocialProvider;

public interface OAuth2UserInfo {
    SocialProvider provider();

    String providerId();

    String email();

    String name();
}
