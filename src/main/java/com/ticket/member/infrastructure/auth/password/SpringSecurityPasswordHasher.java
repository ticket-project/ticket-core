package com.ticket.member.infrastructure.auth.password;

import com.ticket.member.application.auth.password.PasswordHasher;
import com.ticket.member.domain.member.model.EncodedPassword;
import com.ticket.member.domain.member.model.RawPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
