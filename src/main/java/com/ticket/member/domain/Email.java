package com.ticket.member.domain;

import java.util.Objects;

import jakarta.persistence.Embeddable;

@Embeddable
public class Email {
    private String email;

    protected Email() {
        // JPA 전용 생성자다. validate(null)이 돌려주는 값과 같은 빈 문자열로 채워 email이 null이 되지 않게 한다.
        this.email = "";
    }

    private Email(final String email) {
        this.email = validate(email);
    }

    private static String validate(final String email) {
        if (email == null) {
            return "";
        }
        return email.trim();
    }

    public static Email create(final String value) {
        return new Email(value);
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Email email = (Email) o;
        return Objects.equals(this.email, email.email);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(email);
    }
}
