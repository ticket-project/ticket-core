package com.ticket.core.app.auth;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.auth.password.PasswordHasher;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.member.model.RawPassword;
import com.ticket.core.domain.member.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class CredentialAuthenticatorTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private CredentialAuthenticator credentialAuthenticator;

    @Test
    void 로그인시_회원이_없으면_타이밍가드용_해싱_후_인증예외를_던진다() {
        //given
        when(memberRepository.findActiveByEmail("missing@example.com")).thenReturn(Optional.empty());

        //when
        //then
        assertThatThrownBy(() -> credentialAuthenticator.authenticate("missing@example.com", "password123!"))
                .isInstanceOf(CoreException.class);

        verify(passwordHasher).hash(RawPassword.create("timing-guard-dummy-password"));
    }

    @Test
    void 로그인시_저장된_비밀번호가_없으면_인증예외를_던진다() {
        //given
        Member member = Member.createSocialMember(Email.create("social@example.com"), "홍길동", Role.MEMBER);
        when(memberRepository.findActiveByEmail("social@example.com")).thenReturn(Optional.of(member));

        //when
        //then
        assertThatThrownBy(() -> credentialAuthenticator.authenticate("social@example.com", "password123!"))
                .isInstanceOf(CoreException.class);
    }

    @Test
    void 로그인시_비밀번호가_일치하지_않으면_인증예외를_던진다() {
        //given
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "홍길동", Role.MEMBER);
        when(memberRepository.findActiveByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordHasher.matches(RawPassword.create("wrong-password"), EncodedPassword.create("encoded"))).thenReturn(false);

        //when
        //then
        assertThatThrownBy(() -> credentialAuthenticator.authenticate("user@example.com", "wrong-password"))
                .isInstanceOf(CoreException.class);
    }

    @Test
    void 로그인시_비밀번호가_일치하면_회원을_반환한다() {
        //given
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "홍길동", Role.MEMBER);
        when(memberRepository.findActiveByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordHasher.matches(RawPassword.create("password123!"), EncodedPassword.create("encoded"))).thenReturn(true);

        //when
        Member result = credentialAuthenticator.authenticate("user@example.com", "password123!");

        //then
        assertThat(result).isSameAs(member);
    }
}
