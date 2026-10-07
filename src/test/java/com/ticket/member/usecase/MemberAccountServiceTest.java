package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.api.SocialProvider;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.shared.exception.NotFoundException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class MemberAccountServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SocialAccountProvisioningService socialAccountProvisioningService;

    @Mock
    private WithdrawMemberUseCase withdrawMemberUseCase;

    @Test
    void 소셜_연결은_회원_엔티티를_공개하지_않고_상태만_돌려준다() {
        final SocialIdentity identity =
                new SocialIdentity(SocialProvider.KAKAO, "kakao-1", "user@example.com", true, "홍길동");
        final Member member = Member.createSocialMember(Email.create("user@example.com"), "홍길동", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 7L);
        when(socialAccountProvisioningService.getOrCreateMember(identity)).thenReturn(member);

        final AuthenticatedMember memberIdentity = service().resolveSocialAccount(identity);

        assertThat(memberIdentity).isEqualTo(new AuthenticatedMember(7L, "MEMBER"));
    }

    @Test
    void 활성_확인은_없는_회원이면_찾을_수_없다는_실패를_낸다() {
        when(memberRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getActiveIdentity(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 활성_확인은_회원의_번호와_역할을_돌려준다() {
        final Member member = Member.createSocialMember(Email.create("user@example.com"), "홍길동", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 42L);
        when(memberRepository.findActiveById(42L)).thenReturn(Optional.of(member));

        assertThat(service().getActiveIdentity(42L)).isEqualTo(new AuthenticatedMember(42L, "MEMBER"));
    }

    private MemberAccountService service() {
        return new MemberAccountService(memberRepository, socialAccountProvisioningService, withdrawMemberUseCase);
    }
}
