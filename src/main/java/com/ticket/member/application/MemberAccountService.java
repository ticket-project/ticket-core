package com.ticket.member.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.member.api.SocialAccountConnection;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.application.port.PasswordHasher;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.member.exception.DuplicateEmailException;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link MemberAccountApi}의 member 소유 구현이다. 계정 다섯 연산(등록·자격 증명 확인·활성 확인·소셜 연결·탈퇴)이 여기서 한 번에 읽힌다.
 *
 * <p>각 연산이 <b>자기 트랜잭션을 직접 소유한다.</b> 다른 module은 이 계약을 인터페이스로 주입받아 밖에서 호출하므로 Spring proxy를 그대로 통과한다 —
 * 이 클래스 안에서 자기 public method를 다시 부르면 그 순간 트랜잭션이 사라진다(self-invocation).
 *
 * <p>비밀번호 해시는 이 경계 밖으로 나가지 않는다. 해싱과 일치 확인을 member가 직접 하므로 {@code member -> security} 의존이 생기지 않는다.
 * 반대로 <b>외부 provider 연결 해제는 여기서 하지 않는다</b> — 탈퇴는 DB 처리만 하고 연결 목록을 돌려주며, 실제 unlink는 security가 커밋 뒤에
 * 수행한다. 외부 호출을 DB 트랜잭션 안으로 끌어들이지 않기 위해서다.
 *
 * <p>소셜 계정 해석만 {@link OAuth2MemberProvisioningService}가 따로 소유한다. 검증된 이메일 판정, 기존 계정 연결, provider 충돌
 * 판단이 독립적으로 복잡한 정책이라 흐름을 가리지 않고 이름으로 드러나는 편이 낫다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberAccountService implements MemberAccountApi {
    /** 회원이 없어도 같은 비용의 해싱을 한 번 수행해, 응답 시간으로 계정 존재 여부가 드러나지 않게 한다. */
    private static final String TIMING_GUARD_DUMMY_PASSWORD = "timing-guard-dummy-password";

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;
    private final OAuth2MemberProvisioningService oauth2MemberProvisioningService;
    private final Clock clock;

    @Override
    @Transactional
    public Long register(final String email, final RawPassword password, final String name) {
        final Email createdEmail = Email.create(email);
        final Member member =
                new Member(createdEmail, passwordHasher.hash(password), name, Role.MEMBER);

        try {
            return memberRepository.save(member).getId();
        } catch (final DataIntegrityViolationException exception) {
            // 이메일 주소 자체는 남기지 않는다.
            log.warn("이메일 중복 회원가입 시도: {}", createdEmail);
            throw new DuplicateEmailException();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>없는 계정과 비밀번호 불일치를 구분하지 않는다.</b> 둘 다 같은 {@link UnauthenticatedException}(401, E1000)을
     * 던지고, 없는 계정에도 더미 해싱을 수행해 응답 시간까지 맞춘다. 저장된 비밀번호가 없는 소셜 전용 회원도 같은 실패로 다룬다.
     */
    @Override
    @Transactional(readOnly = true)
    public MemberStatus authenticate(final String email, final RawPassword password) {
        final Optional<Member> activeMember = memberRepository.findActiveByEmail(email);

        if (activeMember.isEmpty()) {
            passwordHasher.hash(RawPassword.create(TIMING_GUARD_DUMMY_PASSWORD));
            throw new UnauthenticatedException();
        }

        final Member member = activeMember.get();
        if (member.getEncodedPassword() == null
                || !passwordHasher.matches(password, member.getEncodedPassword())) {
            throw new UnauthenticatedException();
        }
        return toStatus(member);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberStatus requireActiveIdentity(final long memberId) {
        return toStatus(
                memberRepository
                        .findActiveById(memberId)
                        .orElseThrow(() -> new NotFoundException()));
    }

    /** 트랜잭션은 {@link OAuth2MemberProvisioningService}가 그대로 소유한다 — 여기서 다시 열지 않는다. */
    @Override
    public MemberStatus resolveSocialAccount(final SocialIdentity identity) {
        return toStatus(oauth2MemberProvisioningService.getOrCreateMember(identity));
    }

    /**
     * {@inheritDoc}
     *
     * <p>소셜 계정은 회원 aggregate의 자식이라 컬렉션으로 접근한다. row를 지우지 않고 {@code deletedAt}만 채우는 <b>soft
     * delete</b>이며, 외부 provider 연결 해제에 쓸 식별자는 <b>탈퇴 처리로 값이 바뀌기 전에 먼저 모아 둔다</b> — 순서를 뒤집으면 익명화된 식별자로
     * unlink를 보내게 된다. aggregate 내부 상태 변경은 {@link Member#withdraw}가 한 번에 수행한다.
     */
    @Override
    @Transactional
    public List<SocialAccountConnection> withdraw(final long memberId) {
        final LocalDateTime now = LocalDateTime.now(clock);
        final Member member =
                memberRepository
                        .findActiveById(memberId)
                        .orElseThrow(() -> new NotFoundException());
        final List<SocialAccountConnection> socialAccounts =
                member.activeSocialAccounts().stream()
                        .map(
                                account ->
                                        new SocialAccountConnection(
                                                account.getSocialProvider(), account.getSocialId()))
                        .toList();

        member.withdraw(now);

        return socialAccounts;
    }

    private MemberStatus toStatus(final Member member) {
        return new MemberStatus(member.getId(), !member.isDeleted(), member.getRole().name());
    }
}
