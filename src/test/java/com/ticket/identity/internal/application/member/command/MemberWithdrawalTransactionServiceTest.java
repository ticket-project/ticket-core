package com.ticket.identity.internal.application.member.command;

import com.ticket.identity.internal.domain.member.model.Email;
import com.ticket.identity.internal.domain.member.model.EncodedPassword;
import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.model.MemberSocialAccount;
import com.ticket.identity.internal.domain.member.model.Role;
import com.ticket.identity.internal.domain.member.model.SocialProvider;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import com.ticket.identity.internal.domain.member.repository.MemberSocialAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class MemberWithdrawalTransactionServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSocialAccountRepository memberSocialAccountRepository;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void withdraw_marks_member_and_social_accounts_with_same_clock_time() {
        MemberWithdrawalTransactionService memberWithdrawalTransactionService = new MemberWithdrawalTransactionService(
                memberRepository,
                memberSocialAccountRepository,
                fixedClock
        );
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "tester", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 3L);
        MemberSocialAccount kakao = MemberSocialAccount.create(member, SocialProvider.KAKAO, "kakao-123");
        MemberSocialAccount google = MemberSocialAccount.create(member, SocialProvider.GOOGLE, "google-123");
        ReflectionTestUtils.setField(kakao, "id", 1L);
        ReflectionTestUtils.setField(google, "id", 2L);
        when(memberRepository.findActiveById(3L)).thenReturn(Optional.of(member));
        when(memberSocialAccountRepository.findAllActiveByMember(member)).thenReturn(List.of(kakao, google));

        List<String> kakaoIds = memberWithdrawalTransactionService.withdraw(3L);

        assertThat(kakaoIds).containsExactly("kakao-123");
        assertThat(member.getDeletedAt()).isEqualTo(expectedNow);
        assertThat(kakao.getDeletedAt()).isEqualTo(expectedNow);
        assertThat(google.getDeletedAt()).isEqualTo(expectedNow);
        verify(memberRepository).findActiveById(3L);
        verify(memberSocialAccountRepository).findAllActiveByMember(member);
    }
}
