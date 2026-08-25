package com.ticket.core.domain.auth.oauth2;

import com.ticket.core.domain.member.model.SocialProvider;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String providerId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String email() {
        return Optional.ofNullable(getMap(attributes, "kakao_account"))
                .map(account -> (String) account.get("email"))
                .orElse(null);
    }

    @Override
    public String name() {
        return Optional.ofNullable(getMap(attributes, "kakao_account"))
                .map(account -> getMap(account, "profile"))
                .map(profile -> (String) profile.get("nickname"))
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(final Map<String, Object> source, final String key) {
        final Object value = source.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return null;
    }
}

