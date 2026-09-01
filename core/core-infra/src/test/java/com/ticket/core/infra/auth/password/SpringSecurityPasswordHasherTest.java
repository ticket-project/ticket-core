package com.ticket.core.infra.auth.password;

import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.member.model.RawPassword;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class SpringSecurityPasswordHasherTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SpringSecurityPasswordHasher passwordHasher;

    @Test
    void 비밀번호_해싱을_위임한다() {
        when(passwordEncoder.encode("password123!")).thenReturn("encoded");

        EncodedPassword encoded = passwordHasher.hash(RawPassword.create("password123!"));

        assertThat(encoded).isEqualTo(EncodedPassword.create("encoded"));
        verify(passwordEncoder).encode("password123!");
    }

    @Test
    void 비밀번호_일치여부_검증을_위임한다() {
        RawPassword rawPassword = RawPassword.create("password123!");
        EncodedPassword encodedPassword = EncodedPassword.create("encoded");
        when(passwordEncoder.matches("password123!", "encoded")).thenReturn(true);

        boolean matched = passwordHasher.matches(rawPassword, encodedPassword);

        assertThat(matched).isTrue();
        verify(passwordEncoder).matches("password123!", "encoded");
    }
}
