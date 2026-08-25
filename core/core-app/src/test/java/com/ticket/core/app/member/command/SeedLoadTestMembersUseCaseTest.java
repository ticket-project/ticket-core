package com.ticket.core.app.member.command;

import com.ticket.core.app.auth.PasswordService;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.Role;
import com.ticket.core.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
class SeedLoadTestMembersUseCaseTest {

    private static final SeedLoadTestMembersUseCase.Input INPUT =
            new SeedLoadTestMembersUseCase.Input("loadtest", "@test.com", 100, "password1234");

    @Test
    void 기본_계정을_인코딩된_비밀번호로_만든다() {
        final MemberRepository memberRepository = mock(MemberRepository.class);
        final PasswordService passwordService = mock(PasswordService.class);
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(passwordService.encode("password1234")).thenReturn("{noop}encoded-password");
        final SeedLoadTestMembersUseCase useCase =
                new SeedLoadTestMembersUseCase(memberRepository, passwordService);

        final SeedLoadTestMembersUseCase.Output output = useCase.execute(INPUT);

        final ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, times(100)).save(memberCaptor.capture());
        final List<Member> members = memberCaptor.getAllValues();

        assertThat(output.requested()).isEqualTo(100);
        assertThat(output.created()).isEqualTo(100);
        assertThat(members.get(0).getEmail().getEmail()).isEqualTo("loadtest1@test.com");
        assertThat(members.get(99).getEmail().getEmail()).isEqualTo("loadtest100@test.com");
        assertThat(members)
                .allSatisfy(member -> {
                    assertThat(member.getEncodedPassword().getPassword()).isEqualTo("{noop}encoded-password");
                    assertThat(member.getEncodedPassword().getPassword()).isNotEqualTo("password1234");
                    assertThat(member.getRole()).isEqualTo(Role.MEMBER);
                });
        verify(passwordService, times(1)).encode("password1234");
    }

    @Test
    void 이미_있는_계정은_건너뛴다() {
        final MemberRepository memberRepository = mock(MemberRepository.class);
        final PasswordService passwordService = mock(PasswordService.class);
        final Member existingMember = mock(Member.class);
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existingMember));
        when(passwordService.encode("password1234")).thenReturn("{noop}encoded-password");
        final SeedLoadTestMembersUseCase useCase =
                new SeedLoadTestMembersUseCase(memberRepository, passwordService);

        final SeedLoadTestMembersUseCase.Output output = useCase.execute(INPUT);

        verify(memberRepository, never()).save(any(Member.class));
        assertThat(output.created()).isZero();
    }
}
