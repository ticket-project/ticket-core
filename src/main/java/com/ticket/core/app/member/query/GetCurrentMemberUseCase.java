package com.ticket.core.app.member.query;

import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class GetCurrentMemberUseCase {
    private final MemberRepository memberRepository;

    public Output execute(final Input input) {
        final Member findMember = memberRepository.findActiveById(input.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA));
        return new Output(
                findMember.getId(),
                Optional.ofNullable(findMember.getEmail()).map(email -> email.getEmail()).orElse(""),
                findMember.getName(),
                findMember.getRole().name()
        );
    }

    public record Input(Long memberId) {
        public Input {
            RequiredInput.positiveId(memberId, "memberId");
        }
    }

    public record Output(Long memberId, String email, String name, String role) {
    }
}
