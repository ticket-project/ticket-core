package com.ticket.core.domain.member.model;

public enum SocialProvider {
    GOOGLE("구글"),
    KAKAO("카카오");

    private final String description;

    SocialProvider(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
