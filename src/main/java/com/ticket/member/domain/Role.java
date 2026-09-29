package com.ticket.member.domain;

public enum Role {
    ADMIN,
    MEMBER;

    public String getCode() {
        return name();
    }
}
