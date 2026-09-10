package com.ticket.member.oauth.application;

import com.ticket.member.oauth.domain.OAuth2UserInfo;
import com.ticket.member.account.domain.Member;
import com.ticket.member.account.domain.MemberRepository;
import com.ticket.member.account.domain.Email;
import com.ticket.member.account.domain.Role;
import com.ticket.member.account.domain.SocialProvider;
import com.ticket.member.support.exception.DuplicateEmailException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class OAuth2MemberProvisioningServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private OAuth2UserInfo userInfo;

    @InjectMocks
    private OAuth2MemberProvisioningService oauth2MemberProvisioningService;

    @Test
    void 활성_소셜계정이_있으면_기존_회원만_반환한다() {
        //given
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        member.addSocialAccount(SocialProvider.KAKAO, "social-1");
        socialProviderAndId("social-1");
        when(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.of(member));

        //when
        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //then
        assertThat(result).isSameAs(member);
        verify(memberRepository, never()).findActiveByEmail(any());
    }

    @Test
    void 같은_이메일의_기존회원에_같은_제공자_연결이_없으면_소셜계정을_추가한다() {
        //given
        Member existingMember = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        socialUserWithEmail("social-1", " user@example.com ");
        when(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("user@example.com"))
                .thenReturn(Optional.of(existingMember));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //when
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        //then
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(result).isSameAs(existingMember);
        assertThat(memberCaptor.getValue()).isSameAs(existingMember);
        assertThat(existingMember.activeSocialAccounts()).hasSize(1);
        assertThat(existingMember.activeSocialAccounts().get(0).getMember()).isSameAs(existingMember);
        assertThat(existingMember.activeSocialAccounts().get(0).getSocialProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(existingMember.activeSocialAccounts().get(0).getSocialId()).isEqualTo("social-1");
    }

    @Test
    void 같은_이메일의_기존회원에_같은_소셜계정이_이미_연결돼있으면_기존회원을_반환한다() {
        //given
        Member existingMember = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        existingMember.addSocialAccount(SocialProvider.KAKAO, "social-1");
        socialUserWithEmail("social-1", "user@example.com");
        when(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("user@example.com"))
                .thenReturn(Optional.of(existingMember));

        //when
        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //then
        assertThat(result).isSameAs(existingMember);
        assertThat(existingMember.activeSocialAccounts()).hasSize(1);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void 같은_이메일이지만_다른_소셜아이디가_연결돼있으면_중복이메일_예외를_던진다() {
        //given
        Member existingMember = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        existingMember.addSocialAccount(SocialProvider.KAKAO, "other-social");
        socialUserWithEmail("social-1", "user@example.com");
        when(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("user@example.com"))
                .thenReturn(Optional.of(existingMember));

        //when
        //then
        assertThatThrownBy(() -> oauth2MemberProvisioningService.getOrCreateMember(userInfo))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void 탈퇴한_소셜계정만_남아있으면_같은_제공자로_다시_연결한다() {
        //given 연결 해제(soft delete)된 계정은 활성 연결로 치지 않는다
        Member existingMember = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        existingMember.addSocialAccount(SocialProvider.KAKAO, "old-social")
                .withdraw(java.time.LocalDateTime.of(2026, 3, 15, 10, 0));
        socialUserWithEmail("social-1", "user@example.com");
        when(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("user@example.com"))
                .thenReturn(Optional.of(existingMember));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        //when
        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //then 중복 예외가 아니라 새 연결이 추가된다
        assertThat(result).isSameAs(existingMember);
        assertThat(existingMember.activeSocialAccounts()).hasSize(1);
        assertThat(existingMember.activeSocialAccounts().get(0).getSocialId()).isEqualTo("social-1");
    }

    @Test
    void 기존회원이_없으면_이메일과_이름을_정규화해_소셜회원을_생성한다() {
        //given
        socialUserWithEmailAndName("social-1", " User@Example.com ", " 사용자 ");
        when(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("user@example.com"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //when
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        //then
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(result).isSameAs(memberCaptor.getValue());
        assertThat(memberCaptor.getValue().getEmail()).isEqualTo(Email.create("user@example.com"));
        assertThat(memberCaptor.getValue().getName()).isEqualTo("사용자");
        // 소셜 계정은 별도 저장이 아니라 회원 저장에 cascade로 함께 실린다
        assertThat(memberCaptor.getValue().activeSocialAccounts()).hasSize(1);
        assertThat(memberCaptor.getValue().activeSocialAccounts().get(0).getSocialId()).isEqualTo("social-1");
    }

    @Test
    void 이메일과_이름이_없으면_제공자기반_대체값으로_생성한다() {
        //given
        socialUserWithEmailAndName("social-1", " ", null);
        when(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("kakao_social-1@social.ticket"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        //when
        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //then
        assertThat(result.getEmail()).isEqualTo(Email.create("kakao_social-1@social.ticket"));
        assertThat(result.getName()).isEqualTo("kakao_social-1");
    }

    private void socialProviderAndId(final String providerId) {
        when(userInfo.provider()).thenReturn(SocialProvider.KAKAO);
        when(userInfo.providerId()).thenReturn(providerId);
    }

    private void socialUserWithEmailAndName(final String providerId, final String email, final String name) {
        socialProviderAndId(providerId);
        when(userInfo.email()).thenReturn(email);
        when(userInfo.name()).thenReturn(name);
    }

    private void socialUserWithEmail(final String providerId, final String email) {
        socialProviderAndId(providerId);
        when(userInfo.email()).thenReturn(email);
    }
}
