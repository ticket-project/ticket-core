package com.ticket.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.api.SocialProvider;

@SuppressWarnings("NonAsciiCharacters")
class MemberTest {
    @Test
    void create_social_member_without_password() {
        Member member = Member.createSocialMember(Email.create("social@example.com"), "tester", Role.MEMBER);

        assertThat(member.getEmail()).isEqualTo(Email.create("social@example.com"));
        assertThat(member.getName()).isEqualTo("tester");
        assertThat(member.getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void withdraw_uses_given_timestamp() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "tester", Role.MEMBER);
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 3, 15, 10, 0);
        ReflectionTestUtils.setField(member, "id", 7L);
        ReflectionTestUtils.setField(member, "legacyPasswordHash", "{bcrypt}legacy");

        member.withdraw(withdrawnAt);

        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getDeletedAt()).isEqualTo(withdrawnAt);
        assertThat(member.getEmail().getEmail()).startsWith("deleted_7_").endsWith("@withdrawn.ticket");
        assertThat(ReflectionTestUtils.getField(member, "legacyPasswordHash"))
                .as("이메일 가입 시절의 비밀번호 해시는 탈퇴 때 지운다")
                .isNull();
    }

    @Test
    void 회원이_탈퇴하면_활성_소셜계정도_같은_시각에_탈퇴한다() {
        final Member member = Member.createSocialMember(Email.create("user@example.com"), "tester", Role.MEMBER);
        final MemberSocialAccount kakao = member.addSocialAccount(SocialProvider.KAKAO, "kakao-123");
        final MemberSocialAccount google = member.addSocialAccount(SocialProvider.GOOGLE, "google-123");
        final LocalDateTime withdrawnAt = LocalDateTime.of(2026, 3, 15, 10, 0);

        member.withdraw(withdrawnAt);

        assertThat(kakao.getDeletedAt()).isEqualTo(withdrawnAt);
        assertThat(google.getDeletedAt()).isEqualTo(withdrawnAt);
        assertThat(member.activeSocialAccounts()).isEmpty();
    }
}
