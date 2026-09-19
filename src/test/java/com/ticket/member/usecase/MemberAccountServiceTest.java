package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.member.api.SocialAccountConnection;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.api.SocialProvider;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.EncodedPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.shared.exception.NotFoundException;

/**
 * {@code MemberAccountApi}의 구현이 계정 다섯 연산에서 실제로 무엇을 보장하는지 고정한다.
 *
 * <p>이 계약은 security의 가입·로그인·갱신·탈퇴 조립이 전부 기대는 지점인데, 그동안 이 클래스 자체를 검증하는 테스트가 없었다. 내부 협력자를 흡수하는 리팩터링을
 * 하기 전에 <b>밖에서 관찰되는 것</b>(반환 값, 실패의 종류와 내용, 트랜잭션 경계)을 먼저 못 박는다.
 */
@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class MemberAccountServiceTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-15T02:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private MemberAccountService service() {
        return new MemberAccountService(
                memberRepository,
                passwordEncoder,
                new OAuth2MemberProvisioningService(memberRepository),
                CLOCK);
    }

    // ── 등록 ────────────────────────────────────────────────────────────────

    @Test
    void 가입은_이메일을_다듬어_저장하고_새_회원_번호를_돌려준다() {
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(withGeneratedId(11L));

        final Long memberId =
                service()
                        .register(
                                "  user@example.com  ", RawPassword.create("password123!"), "홍길동");

        final ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(memberId).isEqualTo(11L);
        assertThat(saved.getValue().getEmail()).isEqualTo(Email.create("user@example.com"));
        assertThat(saved.getValue().getEncodedPassword())
                .isEqualTo(EncodedPassword.create("encoded-password"));
        assertThat(saved.getValue().getName()).isEqualTo("홍길동");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.MEMBER);
    }

    // ── 인증 ────────────────────────────────────────────────────────────────

    @Test
    void 로그인은_활성_회원의_번호와_역할을_돌려준다() {
        when(memberRepository.findActiveByEmail("user@example.com"))
                .thenReturn(Optional.of(memberWithId(42L, passwordMember())));
        when(passwordEncoder.matches("password123!", "encoded")).thenReturn(true);

        assertThat(service().authenticate("user@example.com", RawPassword.create("password123!")))
                .isEqualTo(new MemberStatus(42L, true, "MEMBER"));
    }

    /**
     * 없는 계정과 틀린 비밀번호가 <b>구분되지 않는다</b>는 것이 이 계약의 핵심이다. 한쪽에만 detail을 붙이는 순간 계정 존재 여부가 응답으로 새어 나간다.
     */
    @Test
    void 없는_계정과_틀린_비밀번호는_완전히_같은_실패를_낸다() {
        when(memberRepository.findActiveByEmail("missing@example.com"))
                .thenReturn(Optional.empty());
        when(memberRepository.findActiveByEmail("user@example.com"))
                .thenReturn(Optional.of(passwordMember()));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        final UnauthenticatedException unknownAccount =
                catchUnauthenticated("missing@example.com", "wrong");
        final UnauthenticatedException wrongPassword =
                catchUnauthenticated("user@example.com", "wrong");

        assertThat(unknownAccount.getErrorCode()).isEqualTo(wrongPassword.getErrorCode());
        assertThat(unknownAccount.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(unknownAccount.getData()).isNull();
        assertThat(wrongPassword.getData()).isNull();
    }

    /** 응답 시간까지 같아야 계정 존재 여부가 새지 않는다 — 회원이 없어도 해싱 비용을 그대로 치른다. */
    @Test
    void 없는_계정에도_타이밍_가드용_해싱을_수행한다() {
        when(memberRepository.findActiveByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        catchUnauthenticated("missing@example.com", "password123!");

        verify(passwordEncoder).encode("timing-guard-dummy-password");
    }

    /** 소셜 전용 회원은 저장된 비밀번호가 없다 — 일반 로그인으로는 들어올 수 없다. */
    @Test
    void 비밀번호가_없는_소셜_회원은_일반_로그인에_실패한다() {
        when(memberRepository.findActiveByEmail("social@example.com"))
                .thenReturn(
                        Optional.of(
                                Member.createSocialMember(
                                        Email.create("social@example.com"), "홍길동", Role.MEMBER)));

        assertThat(catchUnauthenticated("social@example.com", "password123!").getData()).isNull();
    }

    // ── 활성 확인 ────────────────────────────────────────────────────────────

    @Test
    void 활성_확인은_없는_회원이면_찾을_수_없다는_실패를_낸다() {
        when(memberRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().requireActiveIdentity(99L))
                .isInstanceOf(NotFoundException.class);
    }

    // ── 소셜 연결 ────────────────────────────────────────────────────────────

    @Test
    void 소셜_연결은_찾은_회원의_상태를_돌려준다() {
        final Member member = memberWithId(7L, passwordMember());
        when(memberRepository.findActiveBySocialAccount(SocialProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.of(member));

        assertThat(
                        service()
                                .resolveSocialAccount(
                                        new SocialIdentity(
                                                SocialProvider.KAKAO,
                                                "kakao-1",
                                                "user@example.com",
                                                true,
                                                "홍길동")))
                .isEqualTo(new MemberStatus(7L, true, "MEMBER"));
    }

    // ── 탈퇴 ────────────────────────────────────────────────────────────────

    /** 연결 목록은 탈퇴 처리로 socialId가 바뀌기 <b>전에</b> 모아야 한다 — 뒤집으면 외부 provider에 쓰레기 ID를 보내게 된다. */
    @Test
    void 탈퇴는_식별자가_바뀌기_전의_소셜_연결을_돌려주고_회원을_탈퇴_처리한다() {
        final Member member = memberWithId(5L, passwordMember());
        member.addSocialAccount(SocialProvider.KAKAO, "kakao-1");
        when(memberRepository.findActiveById(5L)).thenReturn(Optional.of(member));

        final List<SocialAccountConnection> connections = service().withdraw(5L);

        assertThat(connections)
                .containsExactly(new SocialAccountConnection(SocialProvider.KAKAO, "kakao-1"));
        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getDeletedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(member.activeSocialAccounts()).isEmpty();
    }

    @Test
    void 탈퇴는_없는_회원이면_찾을_수_없다는_실패를_낸다() {
        when(memberRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().withdraw(99L)).isInstanceOf(NotFoundException.class);
    }

    // ── 트랜잭션 경계 ─────────────────────────────────────────────────────────

    /**
     * 계정 연산마다 트랜잭션을 <b>누가</b> 소유하는지를 고정한다. 협력자를 흡수하는 리팩터링에서 가장 조용히 깨지는 것이 이 경계다 — 같은 클래스 안에서 부르면
     * Spring proxy가 적용되지 않아 {@code @Transactional}이 아예 걸리지 않는데, 결과 값은 그대로라 행동 테스트로는 드러나지 않는다.
     */
    @Test
    void 계정_연산의_트랜잭션_경계를_고정한다() throws NoSuchMethodException {
        assertWriteTransaction(
                MemberAccountService.class,
                "register",
                String.class,
                RawPassword.class,
                String.class);
        assertReadOnlyTransaction(
                MemberAccountService.class.getMethod(
                        "authenticate", String.class, RawPassword.class));
        assertReadOnlyTransaction(
                MemberAccountService.class.getMethod("requireActiveIdentity", long.class));
        assertWriteTransaction(MemberAccountService.class, "withdraw", long.class);
        // 소셜 연결만 흡수하지 않았다 — 트랜잭션도 그대로 provisioning service가 소유한다.
        assertThat(
                        MemberAccountService.class
                                .getMethod("resolveSocialAccount", SocialIdentity.class)
                                .isAnnotationPresent(Transactional.class))
                .isFalse();
        assertWriteTransaction(OAuth2MemberProvisioningService.class);
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private void assertWriteTransaction(final Class<?> type) {
        final Transactional transactional = type.getAnnotation(Transactional.class);
        assertThat(transactional).as("%s가 쓰기 트랜잭션을 소유한다", type.getSimpleName()).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void assertWriteTransaction(
            final Class<?> type, final String methodName, final Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final Transactional transactional =
                type.getMethod(methodName, parameterTypes).getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("%s.%s가 쓰기 트랜잭션을 소유한다", type.getSimpleName(), methodName)
                .isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void assertReadOnlyTransaction(final Method method) {
        final Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).as("%s가 읽기 전용 트랜잭션을 소유한다", method.getName()).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private UnauthenticatedException catchUnauthenticated(
            final String email, final String password) {
        return (UnauthenticatedException)
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> service().authenticate(email, RawPassword.create(password)));
    }

    private Member passwordMember() {
        return new Member(
                Email.create("user@example.com"),
                EncodedPassword.create("encoded"),
                "홍길동",
                Role.MEMBER);
    }

    private Member memberWithId(final long id, final Member member) {
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private org.mockito.stubbing.Answer<Member> withGeneratedId(final long id) {
        return invocation -> memberWithId(id, invocation.getArgument(0));
    }
}
