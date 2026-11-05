package com.ticket.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.domain.Email;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.Role;
import com.ticket.testsupport.persistence.JpaSliceTestSupport;

/**
 * 감사 시각({@code created_at}·{@code updated_at})이 JVM 기본 시간대가 아니라 도메인 {@link Clock}을 따르는지 고정한다. 슬라이스의 clock은 실제 시각과 다른
 * 날짜로 고정돼 있어, JVM 시계로 찍히면 이 테스트가 실패한다.
 */
@Transactional
@SuppressWarnings("NonAsciiCharacters")
class JpaAuditingConfigTest extends JpaSliceTestSupport {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Test
    void 감사_시각은_도메인_시계로_찍힌다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);

        entityManager.persist(member);
        entityManager.flush();

        assertThat(member.getCreatedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(member.getUpdatedAt()).isEqualTo(LocalDateTime.now(clock));
    }
}
