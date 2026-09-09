package com.ticket.member.application;

import com.ticket.member.domain.Email;
import com.ticket.member.domain.EncodedPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.RawPassword;
import com.ticket.member.domain.Role;
import com.ticket.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 부하 테스트용 회원을 미리 만들어 둔다. 회원 엔티티를 다루는 일이므로 실행 모듈이 아니라
 * 애플리케이션 계층이 맡고, 시드 러너는 설정값만 넘긴다. 이미 있는 이메일은 건너뛴다.
 *
 * <p>{@code com.ticket.seed.SeedDataLoader}가 부하 테스트 회원을 만들기 위해 호출하는 공개
 * 계약이다. package 전체가 아니라 이 타입 하나만 여는 type-level {@code @NamedInterface}다 —
 * 예전에는 이 클래스가 속한 package 자체가 {@code @NamedInterface("seed")}였다(패키지 구조를
 * module → layer → class로 평탄화하며 package 단위 대신 타입 단위로 옮겼다).
 */
@Service
@RequiredArgsConstructor
@org.springframework.modulith.NamedInterface("seed")
public class SeedLoadTestMembersUseCase {

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;

    public record Input(String emailPrefix, String emailSuffix, int count, String rawPassword) {
    }

    public record Output(int requested, int created) {
    }

    public Output execute(final Input input) {
        final int count = Math.max(0, input.count());
        if (count == 0) {
            return new Output(0, 0);
        }

        final EncodedPassword encodedPassword = passwordHasher.hash(RawPassword.create(input.rawPassword()));
        int created = 0;

        for (int memberNo = 1; memberNo <= count; memberNo++) {
            final String email = input.emailPrefix() + memberNo + input.emailSuffix();
            if (memberRepository.findActiveByEmail(email).isPresent()) {
                continue;
            }

            memberRepository.save(new Member(
                    Email.create(email),
                    encodedPassword,
                    input.emailPrefix() + memberNo,
                    Role.MEMBER
            ));
            created++;
        }

        return new Output(count, created);
    }
}
