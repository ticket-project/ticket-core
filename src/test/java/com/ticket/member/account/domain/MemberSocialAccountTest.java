package com.ticket.member.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
class MemberSocialAccountTest {
    @Test
    void matches_same_social_id() {
        Member member =
                new Member(
                        Email.create("user@example.com"),
                        EncodedPassword.create("encoded"),
                        "tester",
                        Role.MEMBER);
        MemberSocialAccount account =
                MemberSocialAccount.create(member, SocialProvider.KAKAO, "kakao-123");

        assertThat(account.isSameSocialId("kakao-123")).isTrue();
        assertThat(account.isSameSocialId("other")).isFalse();
    }

    @Test
    void withdraw_uses_given_timestamp() {
        Member member =
                new Member(
                        Email.create("user@example.com"),
                        EncodedPassword.create("encoded"),
                        "tester",
                        Role.MEMBER);
        MemberSocialAccount account =
                MemberSocialAccount.create(member, SocialProvider.KAKAO, "kakao-123");
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 3, 15, 10, 0);
        ReflectionTestUtils.setField(account, "id", 11L);

        account.withdraw(withdrawnAt);

        assertThat(account.isDeleted()).isTrue();
        assertThat(account.getDeletedAt()).isEqualTo(withdrawnAt);
        assertThat(account.getSocialId()).startsWith("deleted_11_");
        assertThat(account.getSocialId()).isNotEqualTo("kakao-123");
    }
}
