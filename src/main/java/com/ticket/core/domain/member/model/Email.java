package com.ticket.core.domain.member.model;


import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class Email {

    private String email;

    protected Email() {}

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
        if (o == null || getClass() != o.getClass()) return false;
        final Email email = (Email) o;
        return Objects.equals(this.email, email.email);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(email);
    }
}
