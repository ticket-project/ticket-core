package com.ticket.core.app.auth.oauth2;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.auth.oauth2.OAuth2UserInfo;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.member.model.MemberSocialAccount;
import com.ticket.core.domain.member.repository.MemberSocialAccountRepository;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.Role;
import com.ticket.core.domain.member.model.SocialProvider;
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
    private MemberSocialAccountRepository memberSocialAccountRepository;

    @Mock
    private OAuth2UserInfo userInfo;

    @InjectMocks
    private OAuth2MemberProvisioningService oauth2MemberProvisioningService;

    @Test
    void 활성_소셜계정이_있으면_기존_회원만_반환한다() {
        //given
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        MemberSocialAccount socialAccount = MemberSocialAccount.create(member, SocialProvider.KAKAO, "social-1");
        socialProviderAndId("social-1");
        when(memberSocialAccountRepository.findActiveBySocialProviderAndSocialId(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.of(socialAccount));

        //when
        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //then
        assertThat(result).isSameAs(member);
        verify(memberRepository, never()).findByEmail_EmailAndDeletedAtIsNull(any());
    }

    @Test
    void 같은_이메일의_기존회원에_같은_제공자_연결이_없으면_소셜계정을_추가한다() {
        //given
        Member existingMember = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        socialUserWithEmail("social-1", " user@example.com ");
        when(memberSocialAccountRepository.findActiveBySocialProviderAndSocialId(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("user@example.com"))
                .thenReturn(Optional.of(existingMember));
        when(memberSocialAccountRepository.findByMemberAndSocialProviderAndDeletedAtIsNull(existingMember, SocialProvider.KAKAO))
                .thenReturn(Optional.empty());

        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //when
        ArgumentCaptor<MemberSocialAccount> accountCaptor = ArgumentCaptor.forClass(MemberSocialAccount.class);
        //then
        verify(memberSocialAccountRepository).save(accountCaptor.capture());
        assertThat(result).isSameAs(existingMember);
        assertThat(accountCaptor.getValue().getMember()).isSameAs(existingMember);
        assertThat(accountCaptor.getValue().getSocialProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(accountCaptor.getValue().getSocialId()).isEqualTo("social-1");
    }

    @Test
    void 같은_이메일의_기존회원에_같은_소셜계정이_이미_연결돼있으면_기존회원을_반환한다() {
        //given
        Member existingMember = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        MemberSocialAccount linkedAccount = MemberSocialAccount.create(existingMember, SocialProvider.KAKAO, "social-1");
        socialUserWithEmail("social-1", "user@example.com");
        when(memberSocialAccountRepository.findActiveBySocialProviderAndSocialId(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("user@example.com"))
                .thenReturn(Optional.of(existingMember));
        when(memberSocialAccountRepository.findByMemberAndSocialProviderAndDeletedAtIsNull(existingMember, SocialProvider.KAKAO))
                .thenReturn(Optional.of(linkedAccount));

        //when
        Member result = oauth2MemberProvisioningService.getOrCreateMember(userInfo);

        //then
        assertThat(result).isSameAs(existingMember);
        verify(memberSocialAccountRepository, never()).save(any(MemberSocialAccount.class));
    }

    @Test
    void 같은_이메일이지만_다른_소셜아이디가_연결돼있으면_중복이메일_예외를_던진다() {
        //given
        Member existingMember = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        MemberSocialAccount linkedAccount = MemberSocialAccount.create(existingMember, SocialProvider.KAKAO, "other-social");
        socialUserWithEmail("social-1", "user@example.com");
        when(memberSocialAccountRepository.findActiveBySocialProviderAndSocialId(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("user@example.com"))
                .thenReturn(Optional.of(existingMember));
        when(memberSocialAccountRepository.findByMemberAndSocialProviderAndDeletedAtIsNull(existingMember, SocialProvider.KAKAO))
                .thenReturn(Optional.of(linkedAccount));

        //when
        //then
        assertThatThrownBy(() -> oauth2MemberProvisioningService.getOrCreateMember(userInfo))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ApplicationErrorType.MEMBER_DUPLICATE_EMAIL));
    }

    @Test
    void 기존회원이_없으면_이메일과_이름을_정규화해_소셜회원을_생성한다() {
        //given
        socialUserWithEmailAndName("social-1", " User@Example.com ", " 사용자 ");
        when(memberSocialAccountRepository.findActiveBySocialProviderAndSocialId(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("user@example.com"))
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
        verify(memberSocialAccountRepository).save(any(MemberSocialAccount.class));
    }

    @Test
    void 이메일과_이름이_없으면_제공자기반_대체값으로_생성한다() {
        //given
        socialUserWithEmailAndName("social-1", " ", null);
        when(memberSocialAccountRepository.findActiveBySocialProviderAndSocialId(SocialProvider.KAKAO, "social-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail_EmailAndDeletedAtIsNull("kakao_social-1@social.ticket"))
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
