package com.ticket.core.app.member.command;

import com.ticket.core.domain.auth.PasswordService;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.Role;
import com.ticket.core.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 부하 테스트용 회원을 미리 만들어 둔다. 회원 엔티티를 다루는 일이므로 실행 모듈이 아니라
 * 애플리케이션 계층이 맡고, 시드 러너는 설정값만 넘긴다. 이미 있는 이메일은 건너뛴다.
 */
@Service
@RequiredArgsConstructor
public class SeedLoadTestMembersUseCase {

    private final MemberRepository memberRepository;
    private final PasswordService passwordService;

    public record Input(String emailPrefix, String emailSuffix, int count, String rawPassword) {
    }

    public record Output(int requested, int created) {
    }

    public Output execute(final Input input) {
        final int count = Math.max(0, input.count());
        if (count == 0) {
            return new Output(0, 0);
        }

        final String encodedPassword = passwordService.encode(input.rawPassword());
        int created = 0;

        for (int memberNo = 1; memberNo <= count; memberNo++) {
            final String email = input.emailPrefix() + memberNo + input.emailSuffix();
            if (memberRepository.findByEmail_EmailAndDeletedAtIsNull(email).isPresent()) {
                continue;
            }

            memberRepository.save(new Member(
                    Email.create(email),
                    EncodedPassword.create(encodedPassword),
                    input.emailPrefix() + memberNo,
                    Role.MEMBER
            ));
            created++;
        }

        return new Output(count, created);
    }
}
