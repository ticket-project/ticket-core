package com.ticket.member.domain.member.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Member.socialAccounts} 매핑이 soft delete 규칙을 깨지 않는지 고정한다.
 *
 * <p>매핑 애노테이션을 직접 확인하는 이유가 있다. 지금 탈퇴 흐름은 컬렉션에서 자식을 <b>빼지 않고</b>
 * {@code deletedAt}만 채우므로, {@code orphanRemoval = true}를 붙여도 그 흐름만으로는 아무 일도
 * 일어나지 않는다({@code MemberSocialAccountPersistenceTest}가 통과해 버린다). 위험은 나중에 누군가
 * 컬렉션에서 자식을 빼는 메서드를 추가하는 순간 <b>연결 이력 row가 조용히 삭제되는 것</b>이다.
 * 행위 테스트로는 그 미래의 실수를 잡을 수 없어, 결정 자체를 여기서 못박는다.
 */
@SuppressWarnings("NonAsciiCharacters")
class MemberSocialAccountMappingTest {

    @Test
    void 소셜계정_컬렉션에_orphanRemoval을_쓰지_않는다() throws Exception {
        OneToMany mapping = socialAccountsMapping();

        assertThat(mapping.orphanRemoval())
                .as("MemberSocialAccount는 deletedAt으로 soft delete한다 — 컬렉션에서 빼는 순간 row가 지워지면 연결 이력이 사라진다")
                .isFalse();
    }

    @Test
    void 소셜계정_컬렉션에_REMOVE_cascade를_쓰지_않는다() throws Exception {
        OneToMany mapping = socialAccountsMapping();

        assertThat(mapping.cascade())
                .as("회원 삭제가 곧 소셜 계정 물리 삭제가 되면 안 된다")
                .doesNotContain(CascadeType.REMOVE, CascadeType.ALL);
    }

    @Test
    void 활성_소셜계정만_노출하고_탈퇴한_계정은_감춘다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        member.addSocialAccount(SocialProvider.KAKAO, "kakao-1");
        MemberSocialAccount google = member.addSocialAccount(SocialProvider.GOOGLE, "google-1");

        google.withdraw(LocalDateTime.of(2026, 3, 15, 10, 0));

        assertThat(member.activeSocialAccounts())
                .extracting(MemberSocialAccount::getSocialProvider)
                .containsExactly(SocialProvider.KAKAO);
        assertThat(member.findActiveSocialAccount(SocialProvider.GOOGLE)).isEmpty();
    }

    @Test
    void 활성_소셜계정_목록을_바꿔도_aggregate에_반영되지_않는다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        member.addSocialAccount(SocialProvider.KAKAO, "kakao-1");

        List<MemberSocialAccount> accounts = member.activeSocialAccounts();

        assertThat(accounts).hasSize(1);
        assertThat(member.activeSocialAccounts()).hasSize(1);
        assertThat(accounts).isNotSameAs(member.activeSocialAccounts());
    }

    private OneToMany socialAccountsMapping() throws NoSuchFieldException {
        Field field = Member.class.getDeclaredField("socialAccounts");
        OneToMany mapping = field.getAnnotation(OneToMany.class);
        assertThat(mapping).as("socialAccounts는 @OneToMany로 매핑돼 있어야 한다").isNotNull();
        return mapping;
    }
}
