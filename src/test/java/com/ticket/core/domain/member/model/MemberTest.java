package com.ticket.core.domain.member.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class MemberTest {

    @Test
    void create_social_member_without_password() {
        Member member = Member.createSocialMember(Email.create("social@example.com"), "tester", Role.MEMBER);

        assertThat(member.getEmail()).isEqualTo(Email.create("social@example.com"));
        assertThat(member.getName()).isEqualTo("tester");
        assertThat(member.getRole()).isEqualTo(Role.MEMBER);
        assertThat(member.getEncodedPassword()).isNull();
    }

    @Test
    void withdraw_uses_given_timestamp() {
        Member member = new Member(
                Email.create("user@example.com"),
                EncodedPassword.create("encoded-password"),
                "tester",
                Role.MEMBER
        );
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 3, 15, 10, 0);
        ReflectionTestUtils.setField(member, "id", 7L);

        member.withdraw(withdrawnAt);

        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getDeletedAt()).isEqualTo(withdrawnAt);
        assertThat(member.getEmail().getEmail()).startsWith("deleted_7_").endsWith("@withdrawn.ticket");
        assertThat(member.getEncodedPassword()).isNull();
    }
}
