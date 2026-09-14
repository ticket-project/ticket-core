package com.ticket.member;

import java.util.Objects;

import lombok.Getter;

@Getter
public class RawPassword {
    private final String password;

    private RawPassword(final String password) {
        this.password = password;
    }

    public static RawPassword create(final String value) {
        return new RawPassword(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final RawPassword rawPassword = (RawPassword) o;
        return Objects.equals(this.password, rawPassword.password);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(password);
    }
}
