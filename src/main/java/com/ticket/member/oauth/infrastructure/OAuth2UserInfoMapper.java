package com.ticket.member.oauth.infrastructure;

import com.ticket.member.account.domain.SocialProvider;
import com.ticket.member.oauth.domain.OAuth2UserInfo;
import com.ticket.shared.exception.InvalidRequestException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class OAuth2UserInfoMapper {

    private OAuth2UserInfoMapper() {
    }

    public static OAuth2UserInfo map(final String registrationId, final Map<String, Object> attributes) {
        final String normalizedRegistrationId = registrationId.toLowerCase(Locale.ROOT);

        return switch (normalizedRegistrationId) {
            case "google" -> mapGoogle(attributes);
            case "kakao" -> mapKakao(attributes);
            default -> throw new InvalidRequestException("Unsupported social provider: " + registrationId);
        };
    }

    private static OAuth2UserInfo mapGoogle(final Map<String, Object> attributes) {
        return new OAuth2UserInfo(
                SocialProvider.GOOGLE,
                String.valueOf(attributes.get("sub")),
                (String) attributes.get("email"),
                Boolean.TRUE.equals(attributes.get("email_verified")),
                (String) attributes.get("name")
        );
    }

    private static OAuth2UserInfo mapKakao(final Map<String, Object> attributes) {
        final Map<String, Object> account = getMap(attributes, "kakao_account");
        final Map<String, Object> profile = account == null ? null : getMap(account, "profile");
        final boolean emailVerified = account != null
                && Boolean.TRUE.equals(account.get("is_email_valid"))
                && Boolean.TRUE.equals(account.get("is_email_verified"));

        return new OAuth2UserInfo(
                SocialProvider.KAKAO,
                String.valueOf(attributes.get("id")),
                account == null ? null : (String) account.get("email"),
                emailVerified,
                Optional.ofNullable(profile).map(value -> (String) value.get("nickname")).orElse(null)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(final Map<String, Object> source, final String key) {
        final Object value = source.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return null;
    }
}
