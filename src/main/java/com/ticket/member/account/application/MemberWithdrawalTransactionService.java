package com.ticket.member.account.application;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.member.account.domain.Member;
import com.ticket.member.account.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalTransactionService {

    private final MemberRepository memberRepository;
    private final Clock clock;

    /**
     * 회원과 그 소셜 계정을 함께 탈퇴 처리한다.
     *
     * <p>소셜 계정은 회원 aggregate의 자식이라 컬렉션으로 접근한다. <b>row를 지우지 않고
     * {@code deletedAt}만 채우는 soft delete</b>이며, 외부 provider 연결 해제에 쓸 식별자는 탈퇴
     * 처리로 값이 바뀌기 전에 먼저 모아 둔다. aggregate 내부 상태 변경은 {@link Member#withdraw}가
     * 한 번에 수행한다.
     */
    @Transactional
    public List<SocialAccountConnection> withdraw(final Long memberId) {
        final LocalDateTime now = LocalDateTime.now(clock);
        final Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new NotFoundException());
        final List<SocialAccountConnection> socialAccounts = member.activeSocialAccounts().stream()
                .map(account -> new SocialAccountConnection(
                        account.getSocialProvider(),
                        account.getSocialId()))
                .toList();

        member.withdraw(now);

        return socialAccounts;
    }
}
