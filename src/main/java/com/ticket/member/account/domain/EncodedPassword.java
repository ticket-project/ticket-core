package com.ticket.member.account.domain;

import java.util.Objects;

import jakarta.persistence.Embeddable;

import lombok.Getter;

@Getter
@Embeddable
public class EncodedPassword {
    private String password;

    protected EncodedPassword() {}

    private EncodedPassword(final String encodedPassword) {
        this.password = encodedPassword;
    }

    public static EncodedPassword create(final String value) {
        return new EncodedPassword(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EncodedPassword rawPassword = (EncodedPassword) o;
        return Objects.equals(this.password, rawPassword.password);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(password);
    }
}
