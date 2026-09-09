package com.ticket.member.application;

import com.ticket.member.domain.OAuth2UserInfo;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.MemberSocialAccount;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.Role;
import com.ticket.member.exception.DuplicateEmailException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 소셜 계정은 회원 aggregate의 자식이므로 별도 Repository를 거치지 않는다. 소셜 ID로 회원을 찾는
 * 조회만 {@link MemberRepository}가 맡고, 연결 여부 확인과 연결 추가는 {@code Member}가 자기
 * 컬렉션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OAuth2MemberProvisioningService {

    private final MemberRepository memberRepository;

    public Member getOrCreateMember(final OAuth2UserInfo userInfo) {
        return memberRepository.findActiveBySocialAccount(userInfo.provider(), userInfo.providerId())
                .orElseGet(() -> createOrLinkMember(userInfo));
    }

    private Member createOrLinkMember(final OAuth2UserInfo userInfo) {
        final String email = resolveEmail(userInfo);

        return memberRepository.findActiveByEmail(email)
                .map(existingMember -> linkSocialAccount(existingMember, userInfo))
                .orElseGet(() -> createSocialMember(userInfo, email));
    }

    private Member linkSocialAccount(final Member existingMember, final OAuth2UserInfo userInfo) {
        return existingMember.findActiveSocialAccount(userInfo.provider())
                .map(linkedAccount -> validateSameSocialAccount(existingMember, linkedAccount, userInfo))
                .orElseGet(() -> addSocialAccount(existingMember, userInfo));
    }

    private Member validateSameSocialAccount(
            final Member existingMember,
            final MemberSocialAccount linkedAccount,
            final OAuth2UserInfo userInfo
    ) {
        if (!linkedAccount.isSameSocialId(userInfo.providerId())) {
            throw new DuplicateEmailException("Email is already linked to another social account.");
        }
        return existingMember;
    }

    private Member addSocialAccount(final Member existingMember, final OAuth2UserInfo userInfo) {
        existingMember.addSocialAccount(userInfo.provider(), userInfo.providerId());
        return memberRepository.save(existingMember);
    }

    private Member createSocialMember(final OAuth2UserInfo userInfo, final String email) {
        final String displayName = resolveName(userInfo);
        final Member member = Member.createSocialMember(
                Email.create(email),
                displayName,
                Role.MEMBER
        );
        member.addSocialAccount(userInfo.provider(), userInfo.providerId());
        return memberRepository.save(member);
    }

    private String resolveEmail(final OAuth2UserInfo userInfo) {
        if (StringUtils.hasText(userInfo.email())) {
            return userInfo.email().trim().toLowerCase();
        }
        return userInfo.provider().name().toLowerCase() + "_" + userInfo.providerId() + "@social.ticket";
    }

    private String resolveName(final OAuth2UserInfo userInfo) {
        if (StringUtils.hasText(userInfo.name())) {
            return userInfo.name().trim();
        }
        return userInfo.provider().name().toLowerCase() + "_" + userInfo.providerId();
    }
}
