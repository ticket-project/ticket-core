package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.api.MemberWithdrawn;
import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.api.SocialProvider;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.EncodedPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.shared.exception.NotFoundException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class WithdrawMemberUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T02:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void 탈퇴는_식별자가_바뀌기_전의_소셜_연결을_돌려주고_회원을_탈퇴_처리한다() {
        final Member member =
                new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "홍길동", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 5L);
        member.addSocialAccount(SocialProvider.KAKAO, "kakao-1");
        when(memberRepository.findActiveById(5L)).thenReturn(Optional.of(member));

        final List<SocialAccountSnapshot> connections = useCase().execute(5L);

        assertThat(connections).containsExactly(new SocialAccountSnapshot(SocialProvider.KAKAO, "kakao-1"));
        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getDeletedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(member.activeSocialAccounts()).isEmpty();
        verify(eventPublisher).publishEvent(new MemberWithdrawn(5L));
    }

    @Test
    void 탈퇴는_없는_회원이면_찾을_수_없다는_실패를_낸다() {
        when(memberRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(99L)).isInstanceOf(NotFoundException.class);
    }

    private WithdrawMemberUseCase useCase() {
        return new WithdrawMemberUseCase(memberRepository, CLOCK, eventPublisher);
    }
}
