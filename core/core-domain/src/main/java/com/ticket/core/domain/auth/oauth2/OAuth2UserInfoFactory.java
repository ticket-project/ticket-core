package com.ticket.core.domain.auth.oauth2;


import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import java.util.Locale;
import java.util.Map;

public class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {
    }

    public static OAuth2UserInfo create(final String registrationId, final Map<String, Object> attributes) {
        final String normalizedRegistrationId = registrationId.toLowerCase(Locale.ROOT);

        return switch (normalizedRegistrationId) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "kakao" -> new KakaoOAuth2UserInfo(attributes);
            default -> throw new CoreException(DomainErrorType.INVALID_ARGUMENT, "Unsupported social provider: " + registrationId);
        };
    }
}
