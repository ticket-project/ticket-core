package com.ticket.member.account.application.usecase;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.member.account.domain.Member;
import com.ticket.member.account.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetCurrentMemberUseCase {
    private final MemberRepository memberRepository;

    public Output execute(final Input input) {
        final Member findMember = memberRepository.findActiveById(input.memberId())
                .orElseThrow(() -> new NotFoundException());
        return new Output(
                findMember.getId(),
                Optional.ofNullable(findMember.getEmail()).map(email -> email.getEmail()).orElse(""),
                findMember.getName(),
                findMember.getRole().name()
        );
    }

    public record Input(Long memberId) {
        public Input {
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }
    }

    public record Output(Long memberId, String email, String name, String role) {
    }
}
