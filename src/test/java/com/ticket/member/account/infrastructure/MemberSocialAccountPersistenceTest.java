package com.ticket.member.account.infrastructure;

import com.ticket.member.account.domain.Email;
import com.ticket.member.account.domain.EncodedPassword;
import com.ticket.member.account.domain.Member;
import com.ticket.member.account.domain.Role;
import com.ticket.member.account.domain.SocialProvider;
import com.ticket.member.account.domain.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소셜 계정이 회원 aggregate의 자식 컬렉션으로 바뀐 뒤에도 <b>soft delete가 유지되는지</b>를 실제
 * H2에 붙여 고정한다.
 *
 * <p>이 테스트가 지키는 것은 하나다 — {@code Member.socialAccounts}에 {@code orphanRemoval = true}나
 * {@code CascadeType.REMOVE}가 붙으면 탈퇴 시 {@code MEMBER_SOCIAL_ACCOUNTS} row가 실제로 지워져
 * 연결 이력이 사라진다. 그 실수를 잡으려고 JPA 컬렉션이 아니라 <b>native count</b>로 row 존재를
 * 직접 확인한다.
 *
 * <p>member module만으로 컨텍스트가 서는 좁은 슬라이스라 공용 테스트 베이스를 상속하지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = MemberSocialAccountPersistenceTest.TestApplication.class
)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:member-social-account-persistence;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "management.tracing.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV4,"
                + "org.springframework.modulith.actuator.autoconfigure.ApplicationModulesEndpointConfiguration,"
                + "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration"
})
@Transactional
@SuppressWarnings("NonAsciiCharacters")
class MemberSocialAccountPersistenceTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 회원을_저장하면_소셜계정이_cascade로_함께_저장된다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        member.addSocialAccount(SocialProvider.KAKAO, "kakao-1");

        memberRepository.save(member);
        flushAndClear();

        assertThat(socialAccountRowCount()).isEqualTo(1L);
    }

    @Test
    void 탈퇴해도_소셜계정_row는_남고_deletedAt만_채워진다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        member.addSocialAccount(SocialProvider.KAKAO, "kakao-1");
        member.addSocialAccount(SocialProvider.GOOGLE, "google-1");
        Long memberId = memberRepository.save(member).getId();
        flushAndClear();

        Member found = memberRepository.findActiveById(memberId).orElseThrow();
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        found.activeSocialAccounts().forEach(account -> account.withdraw(now));
        found.withdraw(now);
        flushAndClear();

        // orphanRemoval/CascadeType.REMOVE가 붙었다면 여기서 0이 된다
        assertThat(socialAccountRowCount()).isEqualTo(2L);
        assertThat(deletedSocialAccountRowCount()).isEqualTo(2L);
    }

    @Test
    void 탈퇴한_회원의_소셜계정으로는_다시_로그인되지_않는다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        member.addSocialAccount(SocialProvider.KAKAO, "kakao-1");
        Long memberId = memberRepository.save(member).getId();
        flushAndClear();

        assertThat(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "kakao-1")).isPresent();

        Member found = memberRepository.findActiveById(memberId).orElseThrow();
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        found.activeSocialAccounts().forEach(account -> account.withdraw(now));
        found.withdraw(now);
        flushAndClear();

        assertThat(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "kakao-1")).isEmpty();
    }

    @Test
    void 연결_해제된_소셜계정은_활성_계정에서_빠진다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        member.addSocialAccount(SocialProvider.KAKAO, "kakao-1");
        member.addSocialAccount(SocialProvider.GOOGLE, "google-1");
        Long memberId = memberRepository.save(member).getId();
        flushAndClear();

        Member found = memberRepository.findActiveById(memberId).orElseThrow();
        found.findActiveSocialAccount(SocialProvider.KAKAO).orElseThrow()
                .withdraw(LocalDateTime.of(2026, 3, 15, 10, 0));
        flushAndClear();

        Member reloaded = memberRepository.findActiveById(memberId).orElseThrow();
        assertThat(reloaded.activeSocialAccounts()).hasSize(1);
        assertThat(reloaded.findActiveSocialAccount(SocialProvider.KAKAO)).isEmpty();
        assertThat(reloaded.findActiveSocialAccount(SocialProvider.GOOGLE)).isPresent();
        // 해제된 계정도 row로는 남아 있다
        assertThat(socialAccountRowCount()).isEqualTo(2L);
    }

    @Test
    void 소셜아이디로_회원을_찾을_때_다른_제공자는_걸리지_않는다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        member.addSocialAccount(SocialProvider.KAKAO, "same-id");
        memberRepository.save(member);
        flushAndClear();

        Optional<Member> byKakao = memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "same-id");
        Optional<Member> byGoogle = memberRepository.findActiveBySocialAccount(SocialProvider.GOOGLE, "same-id");

        assertThat(byKakao).isPresent();
        assertThat(byGoogle).isEmpty();
    }

    private long socialAccountRowCount() {
        return ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM MEMBER_SOCIAL_ACCOUNTS")
                .getSingleResult()).longValue();
    }

    private long deletedSocialAccountRowCount() {
        return ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM MEMBER_SOCIAL_ACCOUNTS WHERE deleted_at IS NOT NULL")
                .getSingleResult()).longValue();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    static class AuditingTestConfig {

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("test-auditor");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @TestComponent
    @EntityScan(basePackages = "com.ticket.member")
    @EnableJpaRepositories(basePackages = "com.ticket.member")
    @EnableJpaAuditing
    @Import({MemberRepositoryAdapter.class, AuditingTestConfig.class})
    static class TestApplication {
    }
}
