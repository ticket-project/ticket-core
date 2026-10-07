package com.ticket.member.domain;

import jakarta.persistence.Embeddable;

import lombok.EqualsAndHashCode;

@Embeddable
@EqualsAndHashCode
public class Email {
    private String email;

    protected Email() {
        // JPA 전용 생성자다. create(null)과 같은 빈 문자열로 채워 email이 null이 되지 않게 한다.
        this.email = "";
    }

    private Email(final String email) {
        this.email = email == null ? "" : email.trim();
    }

    public static Email create(final String value) {
        return new Email(value);
    }

    public String getEmail() {
        return email;
    }
}
