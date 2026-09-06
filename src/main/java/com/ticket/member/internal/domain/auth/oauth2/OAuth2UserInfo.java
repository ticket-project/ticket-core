package com.ticket.member.internal.domain.auth.oauth2;

import com.ticket.member.internal.domain.member.model.SocialProvider;

public interface OAuth2UserInfo {
    SocialProvider provider();

    String providerId();

    String email();

    String name();
}
