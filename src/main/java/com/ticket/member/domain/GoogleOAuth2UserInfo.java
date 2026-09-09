package com.ticket.member.domain;

import com.ticket.member.domain.SocialProvider;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class GoogleOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public String providerId() {
        return String.valueOf(attributes.get("sub"));
    }

    @Override
    public String email() {
        return (String) attributes.get("email");
    }

    @Override
    public String name() {
        return (String) attributes.get("name");
    }
}
