package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.api.SocialProvider;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.EncodedPassword;
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
    private PasswordEncoder passwordEncoder;

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

        final MemberStatus status = service().resolveSocialAccount(identity);

        assertThat(status).isEqualTo(new MemberStatus(7L, true, "MEMBER"));
    }

    @Test
    void 로그인은_활성_회원의_번호와_역할을_돌려준다() {
        final Member member = passwordMember();
        ReflectionTestUtils.setField(member, "id", 42L);
        when(memberRepository.findActiveByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("password123!", "encoded")).thenReturn(true);

        assertThat(service().authenticate("user@example.com", RawPassword.create("password123!")))
                .contains(new MemberStatus(42L, true, "MEMBER"));
    }

    @Test
    void 없는_계정과_틀린_비밀번호는_같은_실패_결과를_낸다() {
        when(memberRepository.findActiveByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("user@example.com")).thenReturn(Optional.of(passwordMember()));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThat(service().authenticate("missing@example.com", RawPassword.create("wrong")))
                .isEmpty();
        assertThat(service().authenticate("user@example.com", RawPassword.create("wrong")))
                .isEmpty();
        verify(passwordEncoder).encode("timing-guard-dummy-password");
    }

    @Test
    void 비밀번호가_없는_소셜_회원은_일반_로그인에_실패한다() {
        when(memberRepository.findActiveByEmail("social@example.com"))
                .thenReturn(
                        Optional.of(Member.createSocialMember(Email.create("social@example.com"), "홍길동", Role.MEMBER)));

        assertThat(service().authenticate("social@example.com", RawPassword.create("password123!")))
                .isEmpty();
    }

    @Test
    void 활성_확인은_없는_회원이면_찾을_수_없다는_실패를_낸다() {
        when(memberRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getActiveIdentity(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 활성_확인은_회원의_번호와_역할을_돌려준다() {
        final Member member = passwordMember();
        ReflectionTestUtils.setField(member, "id", 42L);
        when(memberRepository.findActiveById(42L)).thenReturn(Optional.of(member));

        assertThat(service().getActiveIdentity(42L)).isEqualTo(new MemberStatus(42L, true, "MEMBER"));
    }

    private MemberAccountService service() {
        return new MemberAccountService(
                memberRepository, passwordEncoder, oauth2MemberProvisioningService, withdrawMemberUseCase);
    }

    private Member passwordMember() {
        return new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "홍길동", Role.MEMBER);
    }
}
