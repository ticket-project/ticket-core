package com.ticket.member.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import org.springframework.stereotype.Service;

import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCurrentMemberUseCase {
    private final MemberRepository memberRepository;

    public Output execute(final Input input) {
        final Member findMember =
                memberRepository
                        .findActiveById(input.memberId())
                        .orElseThrow(() -> new NotFoundException());
        return new Output(
                findMember.getId(),
                findMember.getEmail().getEmail(),
                findMember.getName(),
                findMember.getRole().name());
    }

    public record Input(Long memberId) {
        public Input {
            requirePositiveId(memberId, "memberId");
        }
    }

    public record Output(Long memberId, String email, String name, String role) {}
}
