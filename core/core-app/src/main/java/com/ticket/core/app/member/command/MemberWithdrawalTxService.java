package com.ticket.core.app.member.command;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.MemberSocialAccount;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.member.repository.MemberSocialAccountRepository;
import com.ticket.core.domain.member.model.SocialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalTxService {

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;
    private final Clock clock;

    @Transactional
    public List<String> withdraw(final Long memberId) {
        final LocalDateTime now = LocalDateTime.now(clock);
        final Member member = memberRepository.getActiveById(memberId);
        final List<MemberSocialAccount> socialAccounts = memberSocialAccountRepository.findAllActiveByMember(member);

        final List<String> kakaoSocialIds = socialAccounts.stream()
                .filter(account -> account.getSocialProvider() == SocialProvider.KAKAO)
                .map(MemberSocialAccount::getSocialId)
                .toList();

        socialAccounts.forEach(account -> account.withdraw(now));
        member.withdraw(now);

        return kakaoSocialIds;
    }
}
