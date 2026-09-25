package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.domain.Email;
import com.ticket.member.domain.EncodedPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.shared.exception.InvalidRequestException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class RegisterMemberUseCaseTest {
    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void 가입은_이메일을_다듬어_저장하고_새_회원_번호를_돌려준다() {
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            final Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 11L);
            return member;
        });

        final RegisterMemberUseCase.Output output = new RegisterMemberUseCase(memberRepository, passwordEncoder)
                .execute(new RegisterMemberUseCase.Input("  user@example.com  ", "password123!", "홍길동"));

        final ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(output.memberId()).isEqualTo(11L);
        assertThat(saved.getValue().getEmail()).isEqualTo(Email.create("user@example.com"));
        assertThat(saved.getValue().getEncodedPassword()).isEqualTo(EncodedPassword.create("encoded-password"));
        assertThat(saved.getValue().getName()).isEqualTo("홍길동");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void 필수값이_없으면_회원가입_입력을_거부한다() {
        assertThatThrownBy(() -> new RegisterMemberUseCase.Input("", "password123!", "홍길동"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new RegisterMemberUseCase.Input("user@example.com", "", "홍길동"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new RegisterMemberUseCase.Input("user@example.com", "password123!", ""))
                .isInstanceOf(InvalidRequestException.class);
    }
}
