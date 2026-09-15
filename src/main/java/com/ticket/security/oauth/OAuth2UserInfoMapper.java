package com.ticket.security.oauth;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.ticket.member.api.SocialIdentity;
import com.ticket.member.api.SocialProvider;
import com.ticket.shared.exception.InvalidRequestException;

public final class OAuth2UserInfoMapper {
    private OAuth2UserInfoMapper() {}

    public static SocialIdentity map(
            final String registrationId, final Map<String, Object> attributes) {
        final String normalizedRegistrationId = registrationId.toLowerCase(Locale.ROOT);

        return switch (normalizedRegistrationId) {
            case "google" -> mapGoogle(attributes);
            case "kakao" -> mapKakao(attributes);
            default ->
                    throw new InvalidRequestException(
                            "Unsupported social provider: " + registrationId);
        };
    }

    private static SocialIdentity mapGoogle(final Map<String, Object> attributes) {
        return new SocialIdentity(
                SocialProvider.GOOGLE,
                String.valueOf(attributes.get("sub")),
                (String) attributes.get("email"),
                Boolean.TRUE.equals(attributes.get("email_verified")),
                (String) attributes.get("name"));
    }

    private static SocialIdentity mapKakao(final Map<String, Object> attributes) {
        final Map<String, Object> account = getMap(attributes, "kakao_account");
        final Map<String, Object> profile = account == null ? null : getMap(account, "profile");
        final boolean emailVerified =
                account != null
                        && Boolean.TRUE.equals(account.get("is_email_valid"))
                        && Boolean.TRUE.equals(account.get("is_email_verified"));

        return new SocialIdentity(
                SocialProvider.KAKAO,
                String.valueOf(attributes.get("id")),
                account == null ? null : (String) account.get("email"),
                emailVerified,
                Optional.ofNullable(profile)
                        .map(value -> (String) value.get("nickname"))
                        .orElse(null));
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<String, Object> getMap(
            final Map<String, Object> source, final String key) {
        final Object value = source.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return null;
    }
}
