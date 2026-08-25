package com.ticket.core.infra.auth;

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
class PasswordEncoderPasswordServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordEncoderPasswordService passwordService;

    @Test
    void 비밀번호_인코딩을_위임한다() {
        when(passwordEncoder.encode("password123!")).thenReturn("encoded");

        String encoded = passwordService.encode("password123!");

        assertThat(encoded).isEqualTo("encoded");
        verify(passwordEncoder).encode("password123!");
    }

    @Test
    void 비밀번호_일치여부_검증을_위임한다() {
        RawPassword rawPassword = RawPassword.create("password123!");
        EncodedPassword encodedPassword = EncodedPassword.create("encoded");
        when(passwordEncoder.matches("password123!", "encoded")).thenReturn(true);

        boolean matched = passwordService.matches(rawPassword, encodedPassword);

        assertThat(matched).isTrue();
        verify(passwordEncoder).matches("password123!", "encoded");
    }
}
