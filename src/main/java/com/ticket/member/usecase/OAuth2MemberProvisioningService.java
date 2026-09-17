package com.ticket.member.usecase;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ticket.member.api.SocialIdentity;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.MemberSocialAccount;
import com.ticket.member.domain.Role;
import com.ticket.member.exception.DuplicateEmailException;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 계정은 회원 aggregate의 자식이므로 별도 Repository를 거치지 않는다. 소셜 ID로 회원을 찾는 조회만 {@link MemberRepository}가
 * 맡고, 연결 여부 확인과 연결 추가는 {@code Member}가 자기 컬렉션으로 처리한다. provider가 검증한 이메일만 기존 계정 자동 연결과 회원 이메일에 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OAuth2MemberProvisioningService {
    private final MemberRepository memberRepository;

    /**
     * 소셜 신원으로 회원을 찾거나 만든다. 판정 순서가 곧 정책이다.
     *
     * <ol>
     *   <li>이미 이 provider 계정으로 연결된 회원이 있으면 그 회원이다 — 이메일은 보지 않는다.
     *   <li>없으면 이메일로 기존 회원을 찾는다. <b>provider가 검증한 이메일만</b> 쓰고, 검증되지 않았으면 provider ID 기반 대체 주소로 격리해
     *       남의 계정에 붙지 않게 한다.
     *   <li>같은 이메일의 회원이 없으면 새로 만들어 연결한다.
     *   <li>있는데 이 provider 연결이 없으면 그 회원에 연결을 더한다.
     *   <li>있고 이미 다른 provider 계정 ID로 연결돼 있으면 충돌이다.
     * </ol>
     */
    public Member getOrCreateMember(final SocialIdentity identity) {
        final Optional<Member> linkedMember =
                memberRepository.findActiveBySocialAccount(
                        identity.provider(), identity.providerId());
        if (linkedMember.isPresent()) {
            return linkedMember.get();
        }

        final String email = resolveEmail(identity);
        final Optional<Member> memberWithSameEmail = memberRepository.findActiveByEmail(email);
        if (memberWithSameEmail.isEmpty()) {
            final Member member =
                    Member.createSocialMember(
                            Email.create(email), resolveName(identity), Role.MEMBER);
            member.addSocialAccount(identity.provider(), identity.providerId());
            return memberRepository.save(member);
        }

        final Member existingMember = memberWithSameEmail.get();
        final Optional<MemberSocialAccount> linkedAccount =
                existingMember.findActiveSocialAccount(identity.provider());
        if (linkedAccount.isEmpty()) {
            existingMember.addSocialAccount(identity.provider(), identity.providerId());
            return memberRepository.save(existingMember);
        }
        if (!linkedAccount.get().isSameSocialId(identity.providerId())) {
            throw new DuplicateEmailException("Email is already linked to another social account.");
        }
        return existingMember;
    }

    private String resolveEmail(final SocialIdentity identity) {
        final String email = identity.email();
        if (identity.emailVerified() && StringUtils.hasText(email)) {
            // StringUtils.hasText가 null과 공백을 모두 걸러 낸 뒤이므로 여기서 email은 null일 수 없다.
            return Objects.requireNonNull(email).trim().toLowerCase();
        }
        return identity.provider().name().toLowerCase()
                + "_"
                + identity.providerId()
                + "@social.ticket";
    }

    private String resolveName(final SocialIdentity identity) {
        final String name = identity.name();
        if (StringUtils.hasText(name)) {
            // StringUtils.hasText가 null과 공백을 모두 걸러 낸 뒤이므로 여기서 name은 null일 수 없다.
            return Objects.requireNonNull(name).trim();
        }
        return identity.provider().name().toLowerCase() + "_" + identity.providerId();
    }
}
