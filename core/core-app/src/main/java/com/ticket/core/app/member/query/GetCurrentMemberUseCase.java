package com.ticket.core.app.member.query;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.query.MemberFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetCurrentMemberUseCase {
    private final MemberFinder memberFinder;

    public Output execute(final Input input) {
        final Member findMember = memberFinder.findActiveMemberById(input.memberId());
        return new Output(
                findMember.getId(),
                Optional.ofNullable(findMember.getEmail()).map(email -> email.getEmail()).orElse(""),
                findMember.getName(),
                findMember.getRole().name()
        );
    }

    public record Input(Long memberId) {
        public Input {
            Objects.requireNonNull(memberId, "memberId는 null일 수 없습니다.");
        }
    }

    public record Output(Long memberId, String email, String name, String role) {
    }
}
