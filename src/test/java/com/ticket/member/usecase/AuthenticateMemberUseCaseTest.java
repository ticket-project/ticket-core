package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.EncodedPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.member.exception.UnauthenticatedException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AuthenticateMemberUseCaseTest {
    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void 로그인은_활성_회원의_번호와_역할을_돌려준다() {
        final Member member = passwordMember();
        ReflectionTestUtils.setField(member, "id", 42L);
        when(memberRepository.findActiveByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("password123!", "encoded")).thenReturn(true);

        assertThat(useCase().execute("user@example.com", RawPassword.create("password123!")))
                .isEqualTo(new MemberStatus(42L, true, "MEMBER"));
    }

    @Test
    void 없는_계정과_틀린_비밀번호는_완전히_같은_실패를_낸다() {
        when(memberRepository.findActiveByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("user@example.com")).thenReturn(Optional.of(passwordMember()));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        final UnauthenticatedException unknownAccount = catchUnauthenticated("missing@example.com", "wrong");
        final UnauthenticatedException wrongPassword = catchUnauthenticated("user@example.com", "wrong");

        assertThat(unknownAccount.getErrorCode()).isEqualTo(wrongPassword.getErrorCode());
        assertThat(unknownAccount.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(unknownAccount.getData()).isNull();
        assertThat(wrongPassword.getData()).isNull();
    }

    @Test
    void 없는_계정에도_타이밍_가드용_해싱을_수행한다() {
        when(memberRepository.findActiveByEmail("missing@example.com")).thenReturn(Optional.empty());

        catchUnauthenticated("missing@example.com", "password123!");

        verify(passwordEncoder).encode("timing-guard-dummy-password");
    }

    @Test
    void 비밀번호가_없는_소셜_회원은_일반_로그인에_실패한다() {
        when(memberRepository.findActiveByEmail("social@example.com"))
                .thenReturn(Optional.of(Member.createSocialMember(
                        Email.create("social@example.com"), "홍길동", Role.MEMBER)));

        assertThat(catchUnauthenticated("social@example.com", "password123!").getData()).isNull();
    }

    private AuthenticateMemberUseCase useCase() {
        return new AuthenticateMemberUseCase(memberRepository, passwordEncoder);
    }

    private UnauthenticatedException catchUnauthenticated(final String email, final String password) {
        return (UnauthenticatedException) org.assertj.core.api.Assertions.catchThrowable(
                () -> useCase().execute(email, RawPassword.create(password)));
    }

    private Member passwordMember() {
        return new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "홍길동", Role.MEMBER);
    }
}
