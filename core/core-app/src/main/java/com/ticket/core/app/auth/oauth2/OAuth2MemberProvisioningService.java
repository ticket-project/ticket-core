package com.ticket.core.app.auth.oauth2;

import com.ticket.core.domain.auth.oauth2.OAuth2UserInfo;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.member.model.MemberSocialAccount;
import com.ticket.core.domain.member.repository.MemberSocialAccountRepository;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.Role;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class OAuth2MemberProvisioningService {

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;

    public Member getOrCreateMember(final OAuth2UserInfo userInfo) {
        return memberSocialAccountRepository.findActiveBySocialProviderAndSocialId(
                        userInfo.provider(),
                        userInfo.providerId()
                )
                .map(MemberSocialAccount::getMember)
                .orElseGet(() -> createOrLinkMember(userInfo));
    }

    private Member createOrLinkMember(final OAuth2UserInfo userInfo) {
        final String email = resolveEmail(userInfo);

        return memberRepository.findByEmail_EmailAndDeletedAtIsNull(email)
                .map(existingMember -> linkSocialAccount(existingMember, userInfo))
                .orElseGet(() -> createSocialMember(userInfo, email));
    }

    private Member linkSocialAccount(final Member existingMember, final OAuth2UserInfo userInfo) {
        return memberSocialAccountRepository.findByMemberAndSocialProviderAndDeletedAtIsNull(existingMember, userInfo.provider())
                .map(linkedAccount -> validateSameSocialAccount(linkedAccount, userInfo))
                .orElseGet(() -> addSocialAccount(existingMember, userInfo));
    }

    private Member validateSameSocialAccount(final MemberSocialAccount linkedAccount, final OAuth2UserInfo userInfo) {
        if (!linkedAccount.isSameSocialId(userInfo.providerId())) {
            throw new CoreException(ErrorType.MEMBER_DUPLICATE_EMAIL, "Email is already linked to another social account.");
        }
        return linkedAccount.getMember();
    }

    private Member addSocialAccount(final Member existingMember, final OAuth2UserInfo userInfo) {
        memberSocialAccountRepository.save(MemberSocialAccount.create(
                existingMember,
                userInfo.provider(),
                userInfo.providerId()
        ));
        return existingMember;
    }

    private Member createSocialMember(final OAuth2UserInfo userInfo, final String email) {
        final String displayName = resolveName(userInfo);
        final Member member = Member.createSocialMember(
                Email.create(email),
                displayName,
                Role.MEMBER
        );
        final Member savedMember = memberRepository.save(member);
        memberSocialAccountRepository.save(MemberSocialAccount.create(
                savedMember,
                userInfo.provider(),
                userInfo.providerId()
        ));
        return savedMember;
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
