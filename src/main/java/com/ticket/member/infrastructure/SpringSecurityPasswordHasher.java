package com.ticket.member.infrastructure;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ticket.member.RawPassword;
import com.ticket.member.application.PasswordHasher;
import com.ticket.member.domain.EncodedPassword;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SpringSecurityPasswordHasher implements PasswordHasher {
    private final PasswordEncoder passwordEncoder;

    @Override
    public EncodedPassword hash(final RawPassword rawPassword) {
        return EncodedPassword.create(passwordEncoder.encode(rawPassword.getPassword()));
    }

    @Override
    public boolean matches(final RawPassword rawPassword, final EncodedPassword encodedPassword) {
        return passwordEncoder.matches(rawPassword.getPassword(), encodedPassword.getPassword());
    }
}
