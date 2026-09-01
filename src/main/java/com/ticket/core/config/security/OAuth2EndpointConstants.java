package com.ticket.core.config.security;

public final class OAuth2EndpointConstants {

    public static final String AUTHORIZATION_BASE_URI = "/api/v1/auth/oauth2/authorize";
    public static final String CALLBACK_BASE_URI_PATTERN = "/api/v1/auth/oauth2/callback/*";

    private OAuth2EndpointConstants() {
    }
}
