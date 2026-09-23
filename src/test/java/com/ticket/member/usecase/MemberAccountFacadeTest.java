package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.api.SocialProvider;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.Role;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class MemberAccountFacadeTest {
    @Mock
    private RegisterMemberUseCase registerMemberUseCase;

    @Mock
    private AuthenticateMemberUseCase authenticateMemberUseCase;

    @Mock
    private GetActiveMemberIdentityUseCase getActiveMemberIdentityUseCase;

    @Mock
    private OAuth2MemberProvisioningService oauth2MemberProvisioningService;

    @Mock
    private WithdrawMemberUseCase withdrawMemberUseCase;

    @Test
    void 소셜_연결은_회원_엔티티를_공개하지_않고_상태만_돌려준다() {
        final SocialIdentity identity =
                new SocialIdentity(SocialProvider.KAKAO, "kakao-1", "user@example.com", true, "홍길동");
        final Member member = Member.createSocialMember(Email.create("user@example.com"), "홍길동", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 7L);
        when(oauth2MemberProvisioningService.getOrCreateMember(identity)).thenReturn(member);

        final MemberStatus status = new MemberAccountFacade(
                        registerMemberUseCase,
                        authenticateMemberUseCase,
                        getActiveMemberIdentityUseCase,
                        oauth2MemberProvisioningService,
                        withdrawMemberUseCase)
                .resolveSocialAccount(identity);

        assertThat(status).isEqualTo(new MemberStatus(7L, true, "MEMBER"));
    }
}
