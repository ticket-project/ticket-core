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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class MemberRegistrarTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private MemberRegistrar memberRegistrar;

    @Test
    void 회원가입시_비밀번호를_해싱해_회원을_저장한다() {
        //given
        Email email = Email.create("user@example.com");
        RawPassword rawPassword = RawPassword.create("password123!");
        when(passwordHasher.hash(rawPassword)).thenReturn(EncodedPassword.create("encoded-password"));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 11L);
            return member;
        });

        Long memberId = memberRegistrar.register(email, rawPassword, "홍길동");

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
        when(passwordHasher.hash(any(RawPassword.class))).thenReturn(EncodedPassword.create("encoded-password"));
        when(memberRepository.save(any(Member.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        //when
        //then
        assertThatThrownBy(() -> memberRegistrar.register(Email.create("user@example.com"), RawPassword.create("password123!"), "홍길동"))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ApplicationErrorType.MEMBER_DUPLICATE_EMAIL));
    }
}
