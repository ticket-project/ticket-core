package com.ticket.core.domain.member.model;

public enum Role {
    ADMIN("관리자"),
    MEMBER("일반회원");

    private final String description;

    Role(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
