package com.ticket.member.api;

import java.util.List;
import java.util.Optional;

/**
 * 다른 module이 회원 계정을 다룰 때 쓰는 공개 계약이다. 회원 테이블과 인증 데이터의 소유권은 member에 있고, 인증 흐름을 조립하는 {@code security}는 이 계약으로만 계정을 만진다.
 *
 * <p><b>비밀번호 해시를 밖으로 내보내지 않는다.</b> 해싱과 일치 확인은 member 안에서 끝나고, 이 계약은 그 결과(회원 번호와 활성 상태)만 돌려준다. 그래서 {@code member ->
 * security} 의존이 생길 이유가 없다.
 *
 * <p>entity·저장소·프레임워크 타입도 노출하지 않는다. 여기 오가는 값은 {@link RawPassword}, {@link SocialIdentity}, {@link MemberStatus},
 * {@link SocialAccountSnapshot}처럼 member가 소유한 공개 값뿐이다.
 *
 * <p>자격 증명 불일치는 빈 결과로 돌려준다. 로그인 경계가 이를 인증 오류로 표현한다. 중복 이메일은 {@code DuplicateEmailException}, 없는 회원은
 * {@code NotFoundException}(404, E404)이다.
 */
public interface MemberAccountApi {
    /** 이메일·비밀번호 자격 증명을 확인한다. 비밀번호 해시는 member 밖으로 나가지 않는다. */
    Optional<MemberStatus> authenticate(String email, RawPassword password);

    /** 지금도 활성 회원인지 확인하고 그 신원을 반환한다. 토큰만 유효하고 계정이 사라진 경우를 걸러내기 위해 토큰 갱신·코드 교환 흐름이 부른다. 존재하지 않거나 탈퇴한 회원이면 던진다. */
    MemberStatus getActiveIdentity(long memberId);

    /** 정규화된 소셜 신원으로 회원을 찾고, 없으면 만들거나 기존 계정에 연결한다. 검증된 이메일만 기존 계정 연결에 쓰고, 식별은 provider와 provider 사용자 ID로 한다. */
    MemberStatus resolveSocialAccount(SocialIdentity identity);

    /**
     * 회원과 소셜 연결을 DB에서 탈퇴 처리한다. 외부 provider 연결 해제는 이 호출 밖에서, 커밋 뒤에 수행한다.
     *
     * @return 외부 연결 해제에 필요한 소셜 연결 목록. 탈퇴 처리로 값이 바뀌기 전에 모아 둔 것이다
     */
    List<SocialAccountSnapshot> withdraw(long memberId);
}
