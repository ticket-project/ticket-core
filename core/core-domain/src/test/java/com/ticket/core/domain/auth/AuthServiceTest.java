package com.ticket.core.domain.auth;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.member.model.RawPassword;
import com.ticket.core.domain.member.model.Role;
import com.ticket.support.error.AuthException;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private AuthService authService;

    @Test
    void 회원가입시_비밀번호를_인코딩해_회원을_저장한다() {
        //given
        Email email = Email.create("user@example.com");
        RawPassword rawPassword = RawPassword.create("password123!");
        when(passwordService.encode("password123!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 11L);
            return member;
        });

        Long memberId = authService.register(email, rawPassword, "홍길동");

        //when
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        //then
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(memberId).isEqualTo(11L);
        assertThat(savedMember.getEmail()).isEqualTo(email);
        assertThat(savedMember.getEncodedPassword()).isEqualTo(EncodedPassword.create("encoded-password"));
        assertThat(savedMember.getName()).isEqualTo("홍길동");
        assertThat(savedMember.getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void 회원가입시_중복_이메일이면_도메인_예외로_변환한다() {
        //given
        when(passwordService.encode("password123!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        //when
        //then
        assertThatThrownBy(() -> authService.register(Email.create("user@example.com"), RawPassword.create("password123!"), "홍길동"))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ErrorType.MEMBER_DUPLICATE_EMAIL));
    }

    @Test
    void 로그인시_회원이_없으면_타이밍가드용_인코딩_후_인증예외를_던진다() {
        //given
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("missing@example.com")).thenReturn(Optional.empty());

        //when
        //then
        assertThatThrownBy(() -> authService.login("missing@example.com", "password123!"))
                .isInstanceOf(AuthException.class);

        verify(passwordService).encode("timing-guard-dummy-password");
    }

    @Test
    void 로그인시_저장된_비밀번호가_없으면_인증예외를_던진다() {
        //given
        Member member = Member.createSocialMember(Email.create("social@example.com"), "홍길동", Role.MEMBER);
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("social@example.com")).thenReturn(Optional.of(member));

        //when
        //then
        assertThatThrownBy(() -> authService.login("social@example.com", "password123!"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void 로그인시_비밀번호가_일치하지_않으면_인증예외를_던진다() {
        //given
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "홍길동", Role.MEMBER);
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("user@example.com")).thenReturn(Optional.of(member));
        when(passwordService.matches(RawPassword.create("wrong-password"), EncodedPassword.create("encoded"))).thenReturn(false);

        //when
        //then
        assertThatThrownBy(() -> authService.login("user@example.com", "wrong-password"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void 로그인시_비밀번호가_일치하면_회원을_반환한다() {
        //given
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "홍길동", Role.MEMBER);
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("user@example.com")).thenReturn(Optional.of(member));
        when(passwordService.matches(RawPassword.create("password123!"), EncodedPassword.create("encoded"))).thenReturn(true);

        //when
        Member result = authService.login("user@example.com", "password123!");

        //then
        assertThat(result).isSameAs(member);
    }
}
